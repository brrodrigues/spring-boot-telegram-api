package rio.brunorodrigues.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.api.TLConfig;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.auth.TLExportedAuthorization;
import org.telegram.api.auth.TLSentCode;
import org.telegram.api.engine.ApiCallback;
import org.telegram.api.engine.AppInfo;
import org.telegram.api.engine.RpcException;
import org.telegram.api.engine.TelegramApi;
import org.telegram.api.functions.auth.TLRequestAuthExportAuthorization;
import org.telegram.api.functions.auth.TLRequestAuthSendCode;
import org.telegram.api.functions.auth.TLRequestAuthSignIn;
import org.telegram.api.functions.help.TLRequestHelpGetConfig;
import org.telegram.api.functions.updates.TLRequestUpdatesGetState;
import org.telegram.api.updates.TLAbsUpdates;
import org.telegram.api.updates.TLUpdatesState;
import org.telegram.api.user.TLAbsUser;
import org.telegram.bot.kernel.engine.MemoryApiState;

import java.io.*;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

@Component
public class TelegramService {

    private final Logger LOGGER = LoggerFactory.getLogger(TelegramService.class);

    private TelegramApi api;

    @Value("${application.telegram.phone-number}")
    String defaultNumber;
    @Value("${application.telegram.api.id}")
    Integer apiId;
    @Value("${application.telegram.api.hash}")
    private String apiHash;

    private MemoryApiState apiState = new MemoryApiState("userTest");

    public void connect() throws Exception {

        api = new TelegramApi(
                this.apiState,
                new AppInfo(apiId, "desktop-pc", "1", "1.0", "pt"),
                new ApiCallback() {
                    @Override
                    public void onAuthCancelled(TelegramApi telegramApi) {

                    }

                    @Override
                    public void onUpdatesInvalidated(TelegramApi telegramApi) {

                    }

                    @Override
                    public void onUpdate(TLAbsUpdates tlAbsUpdates) {

                    }
                });



        TLConfig config = null;

        if (api.getState().isAuthenticated()) {
            api.getState().setAuthenticated(api.getState().getPrimaryDc(),true);
        }else {
            config = api.doRpcCallNonAuth(new TLRequestHelpGetConfig());

            if(config != null){
                this.apiState.setPrimaryDc(config.getThisDc());
                this.apiState.updateSettings(config);

                boolean authenticated = this.apiState.isAuthenticated();

                LOGGER.info("Autenticado ::{}", authenticated);

            } else {
                throw new Exception("config is null, could not update DC List");
            }

            login();
        }
    }


    private void login() throws IOException, TimeoutException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        TLSentCode code = null;

        System.out.println("Enter a Phone Number (Default ist " + defaultNumber + "):");
        //String number = reader.readLine();


        System.out.println("Sending to " + defaultNumber + " ...");
        TLRequestAuthSendCode tlRequestAuthSendCode = new TLRequestAuthSendCode();
        try
        {
            tlRequestAuthSendCode.setApiHash(apiHash);
            tlRequestAuthSendCode.setApiId(284312);
            tlRequestAuthSendCode.setPhoneNumber(defaultNumber);

            code = api.doRpcCallNonAuth(tlRequestAuthSendCode);
        }
        catch (RpcException e)
        {
            if (e.getErrorCode() == 303)
            {
                int destDC = 0;
                if (e.getErrorTag().startsWith("NETWORK_MIGRATE_"))
                {
                    destDC = Integer.parseInt(e.getErrorTag().substring("NETWORK_MIGRATE_".length()));
                }
                else if (e.getErrorTag().startsWith("PHONE_MIGRATE_"))
                {
                    destDC = Integer.parseInt(e.getErrorTag().substring("PHONE_MIGRATE_".length()));
                }
                else if (e.getErrorTag().startsWith("USER_MIGRATE_"))
                {
                    destDC = Integer.parseInt(e.getErrorTag().substring("USER_MIGRATE_".length()));
                }
                else
                {
                    e.printStackTrace();
                }
                api.switchToDc(destDC);
                code = api.doRpcCallNonAuth(tlRequestAuthSendCode);
            }
            else
                e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        String hash = code.getPhoneCodeHash();

        System.out.println("Please Enter the Code:");
        String smsCode = reader.readLine();

        TLRequestAuthSignIn tlRequestAuthSignIn = new TLRequestAuthSignIn();
        tlRequestAuthSignIn.setPhoneCode(smsCode);
        tlRequestAuthSignIn.setPhoneCodeHash(hash);
        tlRequestAuthSignIn.setPhoneNumber(defaultNumber);


        TLAuthorization auth = api.doRpcCallNonAuth(tlRequestAuthSignIn);
        api.getState().setAuthenticated(api.getState().getPrimaryDc(), true);


        TLRequestAuthExportAuthorization tlRequestAuthExportAuthorization = new TLRequestAuthExportAuthorization();

        tlRequestAuthExportAuthorization.setDcId(api.getState().getPrimaryDc());

        //This is where I get the Error
        TLExportedAuthorization test = api.doRpcCall(tlRequestAuthExportAuthorization);
        System.out.println(test.getId());

        String path = Paths.get("").toAbsolutePath().toString();

        FileOutputStream stream = new FileOutputStream( path + File.separator + "test.txt");
        try
        {
            stream.write(test.getBytes().getData());
        }
        finally
        {
            stream.close();
        }

        TLUpdatesState state = api.doRpcCall(new TLRequestUpdatesGetState());
        System.out.println(state.getDate() + "  |  " + state.getPts() + "  |  " + state.getQts() + "  |  " + state.getUnreadCount());


        TLAbsUser user = auth.getUser();

    }

    public int getCurrentUserId() {
        return this.apiState.getUserId();
    }

}

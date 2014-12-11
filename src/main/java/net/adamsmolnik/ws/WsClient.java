package net.adamsmolnik.ws;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import net.adamsmolnik.env.SingleEnvBuilder;

/**
 * @author ASmolnik
 *
 */
public class WsClient {

    class WsEndpoint extends javax.websocket.Endpoint {

        public void onOpen(Session session, EndpointConfig config) {
        }

        public void onClose(Session session, CloseReason closeReason) {
        }

        public void onError(Session session, Throwable throwable) {
        }
    }

    class WsMessageHandler implements MessageHandler.Whole<String> {

        @Override
        public void onMessage(String message) {
            System.out.println("Received: " + message);
        }

    }

    private final Session session;

    public WsClient(String url) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            session = container.connectToServer(new WsEndpoint(), ClientEndpointConfig.Builder.create().build(), new URI(url));
            session.addMessageHandler(new WsMessageHandler());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void send(String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        SingleEnvBuilder seb = new SingleEnvBuilder("student002");
        final String dsElb = seb.buildAndWaitForElb();
        // final String dsElb = "elb-student006-1636985091.us-east-1.elb.amazonaws.com";
        System.out.println("dsElb = " + dsElb);
        try {
            try {
                newWsClient().send("launch;" + dsElb + ";largefiles/file_sizedOf10000000;1;0;1");
            } catch (Exception e) {
                e.printStackTrace();
                TimeUnit.SECONDS.sleep(15);
            }
            newWsClient().send("launch;" + dsElb + ";largefiles/file_sizedOf10000000;100;0;10");
        } catch (Exception e) {
            e.printStackTrace();
            TimeUnit.SECONDS.sleep(30);
            newWsClient().send("launch;" + dsElb + ";largefiles/file_sizedOf10000000;100;0;10");
        }
        TimeUnit.SECONDS.sleep(300);
    }

    private static WsClient newWsClient() {
        return new WsClient("ws://wdsc.adamsmolnik.com/webclient/ws");
    }
}

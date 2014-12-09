package net.adamsmolnik.ws;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

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
        WsClient ws = new WsClient("ws://wdsc.adamsmolnik.com/webclient/ws");
        String dsElb = "elb-student001-353948561.us-east-1.elb.amazonaws.com";
        ws.send("launch;" + dsElb + ";largefiles/file_sizedOf10000000;100;0;10");
        TimeUnit.SECONDS.sleep(30);
    }
}

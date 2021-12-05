package service;

import org.apache.activemq.ActiveMQConnectionFactory;
import service.message.ClientApplicationMessage;
import service.message.QuotationRequestMessage;
import service.message.QuotationResponseMessage;

import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

public class Broker {
    private static Map<Long, service.message.ClientApplicationMessage> cache = new HashMap<>();
    /**
     * This is the starting point for the application. Here, we must get a reference
     * to the Broker Service and then invoke the getQuotations() method on that
     * service.
     *
     * Finally, you should print out all quotations returned by the service.
     *
     */
    public static void main(String[] args) throws JMSException {
        String host = "localhost";

        ConnectionFactory factory =
                new ActiveMQConnectionFactory("failover://tcp://"+host+":61616");

        Connection connection = factory.createConnection();
        connection.setClientID("broker");
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        //Creation of queue and topic for the channels
        Queue requestsQueue = session.createQueue("REQUESTS");

        Queue quotationsQueue = session.createQueue("QUOTATIONS");
        Queue responsesQueue = session.createQueue("RESPONSES");
        Topic topic = session.createTopic("APPLICATIONS");

        MessageConsumer requestConsumer = session.createConsumer(requestsQueue);
        MessageProducer requestProducer = session.createProducer(topic);
        MessageConsumer quotationConsumer = session.createConsumer(quotationsQueue);
        MessageProducer responseProducer = session.createProducer(responsesQueue);

        Thread t1 = new Thread(() -> {
            try {
                connection.start();
                while(true) {
                    Message message = requestConsumer.receive();

                    if (message instanceof ObjectMessage) {
                        Object content = ((ObjectMessage) message).getObject();
                        if (content instanceof QuotationRequestMessage) {
                            QuotationRequestMessage request = (QuotationRequestMessage) content;

                            ClientApplicationMessage CAM = new ClientApplicationMessage(request.id, request.info, new LinkedList<>());
                            cache.put(request.id, CAM);

                            //Creation of the Quotation response message to Client
                            QuotationRequestMessage quotationRequest =
                                    new QuotationRequestMessage(request.id, request.info);

                            Message quotationRequestFromBroker = session.createObjectMessage(quotationRequest);
                            requestProducer.send(quotationRequestFromBroker);
                        }
                        message.acknowledge();
                    } else {
                        System.out.println("Unknown message type: " +
                                message.getClass().getCanonicalName());
                    }
                }
            } catch(JMSException ex){
                ex.printStackTrace();
            }
        });
        t1.start();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.start();
                    while (true) {
                        Message message = quotationConsumer.receive();

                        if (message instanceof ObjectMessage) {
                            Object content = ((ObjectMessage) message).getObject();
                            if (content instanceof QuotationResponseMessage) {
                                QuotationResponseMessage request = (QuotationResponseMessage) content;

                                if(cache.containsKey(request.id)){
                                    cache.get(request.id).addQuotation(request.quotation);
                                }

                                message.acknowledge();
                                new Thread(new Runnable() {
                                    private long currentID;

                                    public Runnable init(long CURRENTID) {
                                        this.currentID = CURRENTID;
                                        return this;
                                    }

                                    @Override
                                    public void run() {
                                        try {
                                            Thread.sleep(10000);
                                            //Creation of the request message
                                            service.message.ClientApplicationMessage quotationResponse = cache.get(currentID);
                                            cache.remove(currentID);
                                            Message quotationResponseToClient = session.createObjectMessage(quotationResponse);
                                            responseProducer.send(quotationResponseToClient);
                                        } catch (InterruptedException | JMSException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }.init(request.id)).start();
                            } else {
                                System.out.println("Unknown message type: " +
                                        message.getClass().getCanonicalName());
                            }
                        }
                    }
                } catch(JMSException ex){
                    ex.printStackTrace();
                }
            }
        });
        t2.start();
    }
}
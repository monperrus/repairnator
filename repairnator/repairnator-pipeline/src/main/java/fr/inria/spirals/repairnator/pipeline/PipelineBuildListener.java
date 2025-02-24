package fr.inria.spirals.repairnator.pipeline;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Message;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.BytesMessage;
import javax.jms.MessageListener;
import javax.jms.MessageConsumer;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;

/**
 * This class fetch build ids from ActiveMQ queue and run the pipeline with it.
 */
public class PipelineBuildListener implements Listener,MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineBuildListener.class);
    private static final RepairnatorConfig config = RepairnatorConfig.getInstance();
    private Launcher launcher;

    public PipelineBuildListener(Launcher launcher){
        this.launcher = launcher;
        LOGGER.warn("KUBERNETES MODE");
    }

    /**
     * Run this as a listener server and fetch one message as a time
     */
    public void runListenerServer() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(config.getActiveMQUrl() + "?jms.prefetchPolicy.all=1");
        Connection connection;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
            Destination queue = session.createQueue(config.getActiveMQListenQueueName());

            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(this);
            LOGGER.warn("Server is now listening for build ids");
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Method implemented from MessageListener and is called 
     * each time this is done with the previous message
     *
     * @param message ActiveMQ message object containing a string buildId.
     */
    public void onMessage(Message message) {
        String messageText = null;
        try {
            message.acknowledge();
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                messageText = textMessage.getText();
                LOGGER.info("A new buildId has arrived: " + messageText);
                config.setBuildId(Integer.parseInt(messageText));
                this.launcher.mainProcess();
            } else if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;
                byte[] data = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(data);
                messageText = new String(data);
                LOGGER.info("A new buildId has arrived: " + messageText);
                config.setBuildId(Integer.parseInt(messageText));
                this.launcher.mainProcess();
            }
            /* Delete the folder when done*/
            this.deleteDir(messageText);
            this.deleteDir("workspace");

            LOGGER.warn("Done repairning. Awaiting for new build ... ");
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }


    /* Helpers */

    public void deleteDir(String dirPath) {
        File directory = new File(dirPath);
        //make sure directory exists
        if(!directory.exists()){
 
           LOGGER.warn("Directory " + dirPath + " does not exist.");
 
        }else{
 
           try{
               
               this.delete(directory);
            
           }catch(IOException e){
               e.printStackTrace();
           }
        }
    }

    public void delete(File file) throws IOException{
        if(file.isDirectory()){
            //directory is empty, then delete it
            if(file.list().length==0){
               file.delete();
            }else{
                
               //list all the directory contents
               String files[] = file.list();
     
               for (String temp : files) {
                  //construct the file structure
                  File fileDelete = new File(file, temp);
                 
                  //recursive delete
                 delete(fileDelete);
               }
                
               //check the directory again, if empty then delete it
               if(file.list().length==0){
                 file.delete();
               }
            }
            
        }else{
            //if file, then delete it
            file.delete();
        }
    }

    public void submitBuild(String buildStr){}
}

package communication;

import communication.messages.Message;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import utils.SystemTimer;
import utils.VirtualTimer;

//TODO:
// * costs of broadcast
public class Channel implements MessageSender, MessageReceiver {
    private Network network;
    private long transmission_speed; /* bytes per second */
    private PrioritySendingQueue sending_queue;
    private LinkedList<Message> received_queue = new LinkedList<Message>();
    private Message transmitted_message = null; /* message being currently transmited, null if no transmission in progress */
    private long queue_millibytes_transmitted; /* millibytes to be transmitted from sending_queue to received_queue */
    private String name;
    private long last_transmission_time; /* time when last transmission happened */
    
    protected Channel(Network network, String name, long transmission_speed, long buffer_size) {
        this.network = network;
        this.last_transmission_time = network.timer().currentMillis();
        this.name = name;
        this.transmission_speed = transmission_speed;
        this.sending_queue = new PrioritySendingQueue(buffer_size);
    }
    
    private void doTransmission() {
        long current_time = network.timer().currentMillis();
        queue_millibytes_transmitted += (current_time-last_transmission_time)*transmission_speed;
        
        if (transmitted_message==null) {
            transmitted_message = sending_queue.removeFirst();
        }
        
        while (transmitted_message!=null&&1000*transmitted_message.length()<queue_millibytes_transmitted) {
            queue_millibytes_transmitted -= 1000*transmitted_message.length();
            received_queue.add(transmitted_message);
            transmitted_message = sending_queue.removeFirst();
        }
        
        if (transmitted_message==null) {
            queue_millibytes_transmitted = 0;
        }
        
        last_transmission_time = current_time;
    }
    
    @Override
    synchronized public boolean sendQueueEmpty() {
        doTransmission();
        return transmitted_message==null;
    }
    
    @Override
    synchronized public boolean receiveQueueEmpty() {
        doTransmission();
        return received_queue.isEmpty();
    }
    
    @Override
    synchronized public long receiveQueueItemsCount() {
        doTransmission();
        return received_queue.size();
    }
    
    @Override
    synchronized public long receiveQueueLength() {
        doTransmission();
        
        long size = 0;
        for (Message message: received_queue) {
            size += message.length();
        }
        return size;

    }            
    
    @Override
    synchronized public Message receive() {
        doTransmission();
        return received_queue.pollFirst();
    }
    
    synchronized private long sendQueueMillisLength() {
        doTransmission();
        long size = 0;
        if (transmitted_message!=null) size += transmitted_message.length();
        size += sending_queue.length();
        return 1000*size-queue_millibytes_transmitted;
    }
    
    @Override
    synchronized public long sendQueueItemsCount() {
        doTransmission();
        return transmitted_message==null? 0: 1+sending_queue.itemsCount();
    }
    
    @Override
    synchronized public long sendQueueLength() {
        return (long)Math.ceil(sendQueueMillisLength()/1000.0);
    }
    
    @Override
    synchronized public double secondsToSendAll() {
        return 0.001*sendQueueMillisLength()/transmission_speed;
    }
    
    @Override
    synchronized public void send(Priority priority, Message message) {
        if (sending_queue.isEmpty()) last_transmission_time = network.timer().currentMillis(); /* reset transmission if queue is empty */
        sending_queue.add(priority, message);
        doTransmission();
    }
    
    @Override
    synchronized public void sendFirst(Priority priority, Message message) {
        if (sending_queue.isEmpty()) last_transmission_time = network.timer().currentMillis(); /* reset transmission if queue is empty */
        sending_queue.addFirst(priority, message);
        doTransmission();
    }
    
    public long transmissionSpeed() {
        return transmission_speed;
    }
    
    public MessageSender sender() {
        return this;
    }
    
    public MessageReceiver receiver() {
        return this;
    }
    
    @Override
    public Channel channel() {
        return this;
    }
    
    public String name() {
        return name;
    }
    
    public void clear() {
        received_queue.clear();
        sending_queue.clear();
        transmitted_message = null;
        queue_millibytes_transmitted = 0;
    }
}

package edu.teco.pavos.transfer.sender;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;

import org.python.core.PyList;
import org.python.core.PyString;
import org.python.modules.cPickle;

import edu.teco.pavos.transfer.config.GraphiteConfig;
import edu.teco.pavos.transfer.converter.GraphiteConverter;
import edu.teco.pavos.transfer.data.ObservationData;
import edu.teco.pavos.transfer.sender.connection.SocketManager;

/**
 * Sends data to Graphite
 */
public class GraphiteSender extends Sender {

	private SocketManager som;

	/**
	 * Generates a {@link GraphiteSender} and establishes a connection to Graphite.
	 */
	public GraphiteSender() {
		this.som = new SocketManager();
		som.connect(GraphiteConfig.getGraphiteHostName(), GraphiteConfig.getGraphitePort());
	}

	/**
	 * Sends the recorded data to Graphite. Uses a record of multiple data objects.
	 * <p>
	 * 
	 * @param records {@link Collection} of {@link ObservationData}
	 * @return sendingSuccessful {@link Boolean}
	 */
	public boolean send(Collection<ObservationData> records) {
		if (som == null)
			return false;
		if (som.isConnectionClosed())
			som.reconnect();
		if (som.isConnectionClosed())
			return false;

		PyList list = new PyList();

		for (ObservationData record : records) {
			GraphiteConverter.addObservations(record, list);
		}

		PyString payload = cPickle.dumps(list);
		byte[] header = ByteBuffer.allocate(4).putInt(payload.__len__()).array();

		if (som.isConnectionClosed()) {
			som.reconnect();
		}

		return writeToGraphite(header, payload);
	}

	@Override
	public void close() {
		som.closeSocket();
	}

	private boolean writeToGraphite(byte[] header, PyString payload) {
		if (som == null || som.isConnectionClosed())
			return false;
		try {
			OutputStream outputStream = som.getOutputStream();
			if (outputStream == null) {
				logger.error("Could not send data to Graphite. OutputStream not connected.");
				return false;
			}
			outputStream.write(header);
			outputStream.write(payload.toBytes());
			outputStream.flush();
			outputStream.close();
			logger.info("Data was sent successfully!");
		} catch (IOException e) {
			logger.error("Failed writing to Graphite.", e);
			return false;
		}

		return true;
	}

}
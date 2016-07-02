package com.github.brotherlogic.javaserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import discovery.Discovery.RegistryEntry;
import discovery.DiscoveryServiceGrpc;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import monitorproto.MonitorServiceGrpc;
import monitorproto.Monitorproto.MessageLog;
import monitorproto.Monitorproto.ValueLog;

public abstract class JavaServer {

	public abstract String getServerName();

	private RegistryEntry registry;

	private String discoveryHost;
	private int discoveryPort;

	private String getIPAddress() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			return "";
		}
	}

	private String getMACAddress() {
		try {
			return new String(NetworkInterface.getNetworkInterfaces().nextElement().getHardwareAddress());
		} catch (SocketException ex) {
			return "";
		}
	}

	private Server server;

	public abstract List<BindableService> getServices();

	private boolean running = true;

	public void Log(String message) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
				.usePlaintext(true).build();
		MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc.newBlockingStub(channel);

		try {
			MessageLog messageLog = MessageLog.newBuilder().setEntry(registry).setMessage(message).build();
			blockingStub.writeMessageLog(messageLog);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to register!");
			e.printStackTrace();
		}
	}

	public void Log(float value) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
				.usePlaintext(true).build();
		MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc.newBlockingStub(channel);

		try {
			ValueLog valueLog = ValueLog.newBuilder().setEntry(registry).setValue(value).build();
			blockingStub.writeValueLog(valueLog);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to register!");
			e.printStackTrace();
		}
	}

	public void Serve(String discoveryHost, int discoveryPort) {

		register(discoveryHost, discoveryPort);

		Thread heartbeat = new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					try {
						System.out.println("Sleeping!");
						Thread.sleep(60 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					System.out.println("Sending heartbeat");
					sendHeartbeat();
				}
			}
		});
		heartbeat.start();

		if (getServices().size() > 0) {
			try {
				ServerBuilder builder = ServerBuilder.forPort(registry.getPort());
				for (BindableService service : getServices())
					builder.addService(service);
				server = builder.build().start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			localServe();
		}
	}

	public abstract void localServe();

	private void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	private void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	/**
	 * Registers us with the discovery server
	 * 
	 * @param host
	 *            Hostname of the discovery server
	 * @param port
	 *            Port number of the discovery server
	 */
	private void register(String host, int port) {
		this.discoveryHost = host;
		this.discoveryPort = port;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		DiscoveryServiceGrpc.DiscoveryServiceBlockingStub blockingStub = DiscoveryServiceGrpc.newBlockingStub(channel);

		RegistryEntry request = RegistryEntry.newBuilder().setName(getServerName()).setIp(getIPAddress())
				.setIdentifier(getMACAddress()).build();
		try {
			registry = blockingStub.registerService(request);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to register!");
			e.printStackTrace();
		}
	}

	private void sendHeartbeat() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
				.usePlaintext(true).build();
		MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc.newBlockingStub(channel);

		try {
			blockingStub.receiveHeartbeat(registry);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to register!");
			e.printStackTrace();
		}
	}

	public String getHost() {
		return registry.getIp();
	}

	public int getPort() {
		return registry.getPort();
	}

	public int getPort(String server) {
		RegistryEntry entry = resolveServer(server);
		if (entry != null) {
			return entry.getPort();
		}
		return 0;
	}

	public String getHost(String server) {
		RegistryEntry entry = resolveServer(server);
		if (entry != null) {
			return entry.getIp();
		}
		return null;
	}

	private RegistryEntry resolveServer(String serverName) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(discoveryHost, discoveryPort).usePlaintext(true)
				.build();
		DiscoveryServiceGrpc.DiscoveryServiceBlockingStub blockingStub = DiscoveryServiceGrpc.newBlockingStub(channel);

		RegistryEntry response = null;
		RegistryEntry request = RegistryEntry.newBuilder().setName(serverName).build();
		try {
			response = blockingStub.discover(request);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to find server: " + serverName);
			e.printStackTrace();
		}

		return response;
	}
}
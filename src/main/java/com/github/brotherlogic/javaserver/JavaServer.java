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

public abstract class JavaServer {

	public abstract String getServerName();

	private RegistryEntry registry;
	private RegistryEntry monitor;

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

	public void Serve(String discoveryHost, int discoveryPort) {

		register(discoveryHost, discoveryPort);

		Thread heartbeat = new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					try {
						Thread.sleep(60 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

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
		ManagedChannel channel = ManagedChannelBuilder.forAddress(monitor.getIp(), monitor.getPort()).usePlaintext(true)
				.build();
		MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc.newBlockingStub(channel);

		try {
			blockingStub.receiveHeartbeat(registry);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to register!");
			e.printStackTrace();
		}
	}

	private void getMonitorDetails(String host, int port) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		DiscoveryServiceGrpc.DiscoveryServiceBlockingStub blockingStub = DiscoveryServiceGrpc.newBlockingStub(channel);

		RegistryEntry request = RegistryEntry.newBuilder().setName("monitor").build();
		try {
			monitor = blockingStub.discover(request);
		} catch (StatusRuntimeException e) {
			System.err.println("Unable to find monitor!");
			e.printStackTrace();
		}
	}
}
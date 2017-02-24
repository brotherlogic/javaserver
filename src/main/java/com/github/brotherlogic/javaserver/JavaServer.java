package com.github.brotherlogic.javaserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

	// From
	// http://stackoverflow.com/questions/6164167/get-mac-address-on-local-machine-with-java
	private static String GetMacAddress(InetAddress ip) {
		String address = null;
		try {

			NetworkInterface network = NetworkInterface.getByInetAddress(ip);
			byte[] mac = network.getHardwareAddress();

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
			}
			address = sb.toString();

		} catch (SocketException e) {

			e.printStackTrace();

		}

		return address;
	}

	private static String GetAddress(String addressType) {
		return GetAddressLocal(addressType, true);
	}

	protected static String GetAddressLocal(String addressType, boolean retry) {
		String address = "";
		InetAddress lanIp = null;
		try {

			String ipAddress = null;
			Enumeration<NetworkInterface> net = null;
			net = NetworkInterface.getNetworkInterfaces();

			while (net.hasMoreElements()) {
				NetworkInterface element = net.nextElement();
				Enumeration<InetAddress> addresses = element.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress ip = addresses.nextElement();
					if (ip instanceof Inet4Address && !ip.getHostAddress().contains("127.0")) {
						if (ip.isSiteLocalAddress()) {
							ipAddress = ip.getHostAddress();
							lanIp = InetAddress.getByName(ipAddress);
						}

					}

				}
			}

			if (lanIp == null) {
				// Sleep for 30 seconds and retry
				if (retry) {
					try {
						Thread.sleep(30 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					return GetAddressLocal(addressType, false);
				}
				return null;
			}

			if (addressType.equals("ip")) {

				address = lanIp.toString().replaceAll("^/+", "");

			} else if (addressType.equals("mac")) {

				address = GetMacAddress(lanIp);

			} else {

				throw new Exception("Specify \"ip\" or \"mac\"");

			}

		} catch (UnknownHostException e) {

			e.printStackTrace();

		} catch (SocketException e) {

			e.printStackTrace();

		} catch (Exception e) {

			e.printStackTrace();

		}

		System.out.println("Returning " + address);
		return address;

	}

	protected String getIPAddress() {
		return GetAddress("ip");
	}

	protected String getMACAddress() {
		return GetAddress("mac");
	}

	private Server server;

	public abstract List<BindableService> getServices();

	private boolean running = true;

	public void Log(String message) {
		String host = getHost("monitor");
		int port = getPort("monitor");

		if (host != null && port > 0) {
			try {
				ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
						.usePlaintext(true).build();
				MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc
						.newBlockingStub(channel);

				MessageLog messageLog = MessageLog.newBuilder().setEntry(registry).setMessage(message).build();
				blockingStub.writeMessageLog(messageLog);

				try {
					channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				System.err.println("Unable to register!");
				e.printStackTrace();
			}
		}
	}

	public void Log(float value) {
		String host = getHost("monitor");
		int port = getPort("monitor");

		if (host != null && port > 0) {
			try {

				ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
						.usePlaintext(true).build();
				MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc
						.newBlockingStub(channel);

				ValueLog valueLog = ValueLog.newBuilder().setEntry(registry).setValue(value).build();
				blockingStub.writeValueLog(valueLog);

				try {
					channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				System.err.println("Unable to register!");
				e.printStackTrace();
			}
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

		try {
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void sendHeartbeat() {
		String host = getHost("monitor");
		int port = getPort("monitor");

		if (host != null && port > 0 && registry != null) {
			ManagedChannel channel = ManagedChannelBuilder.forAddress(getHost("monitor"), getPort("monitor"))
					.usePlaintext(true).build();
			MonitorServiceGrpc.MonitorServiceBlockingStub blockingStub = MonitorServiceGrpc.newBlockingStub(channel);

			try {
				blockingStub.receiveHeartbeat(registry);
			} catch (StatusRuntimeException e) {
				System.err.println("Unable to send heartbeat!");
				e.printStackTrace();
			}
			try {
				channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		String toggle = "off";
		Calendar now = Calendar.getInstance();
		if (now.get(Calendar.HOUR_OF_DAY) >= 7 && now.get(Calendar.HOUR_OF_DAY) < 22) {
			toggle = "on";
		}

		// Turn the display off
		try {
			Process p = Runtime.getRuntime().exec("xset -display :0.0 dpms force " + toggle);
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			for (String line = reader.readLine(); line != null; line = reader.readLine())
				System.out.println("OUT = " + line);
		} catch (Exception e) {
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

		try {
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return response;
	}
}

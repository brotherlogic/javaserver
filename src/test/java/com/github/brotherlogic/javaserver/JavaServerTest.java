package com.github.brotherlogic.javaserver;

import java.util.LinkedList;
import java.util.List;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

import io.grpc.BindableService;

public class JavaServerTest {

	@Test
	public void testGetHostname() throws UnknownHostException{
        JavaServer testServer = new JavaServer() {
            @Override
            public String getServerName() {
                return "testserver";
            }

            @Override
            public List<BindableService> getServices() {
                return new LinkedList<BindableService>();
            }

            @Override
            public void localServe() {
                // Do nothing
            }

        };
		String host = testServer.getHostName();
		System.out.println("HOSTname = " + host);
		Assert.assertTrue("Host is not quite right: " + host, host.length() > 0);
	}

	@Test
	public void testGetIdentifier() {
		JavaServer testServer = new JavaServer() {
			@Override
			public String getServerName() {
				return "testserver";
			}

			@Override
			public List<BindableService> getServices() {
				return new LinkedList<BindableService>();
			}

			@Override
			public void localServe() {
				// Do nothing
			}

		};

		String mac = testServer.getMACAddress();
		System.out.println("MAC = " + mac);
		Assert.assertEquals(mac.length(), "b4:18:d1:eb:6c:29".length());
	}

	@Test
	public void testGetIp() {
		JavaServer testServer = new JavaServer() {
			@Override
			public String getServerName() {
				return "testserver";
			}

			@Override
			public List<BindableService> getServices() {
				return new LinkedList<BindableService>();
			}

			@Override
			public void localServe() {
				// Do nothing
			}

		};

		String ip = testServer.getIPAddress();
		System.out.println("IP = " + ip);
		Assert.assertNotEquals("127.0.1.1", ip);
	}

}
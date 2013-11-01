/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.ws.server;

import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Properties;

import org.apache.jasper.servlet.JspServlet;
import org.apache.uima.ducc.common.config.CommonConfiguration;
import org.apache.uima.ducc.common.internationalization.Messages;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccLoggerComponents;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;


public class DuccWebServer {
	private static DuccLogger logger = DuccLoggerComponents.getWsLogger(DuccWebServer.class.getName());
	private static Messages messages = Messages.getInstance();
	
	private DuccId jobid = null;
	
	/**
	 * DUCC_WEB should be set as an environment variable.  This is the webserver's
	 * base directory where it will find web pages to serve from sub-directory root, 
	 * and SSL data in sub-directory etc.
	 */
	private String ducc_web =".";
	
	/**
	 * The default port can be overridden in ducc.proerties file, for example:
	 * 		ducc.ws.port = 41233
	 */
	private int port = 42133;
	private String ipaddress = null;
	
	/**
	 * To support https, do the following:
	 * 
	 * 1. use 'keytool -keystore keystore -alias jetty -genkey -keyalg RSA -validity 10000' to create
	 *    keystore in ducc_web/etc directory
	 * 2. in ducc.properties set SSL port, for example:
	 * 		ducc.ws.port.ssl = 42155
	 * 3. in ducc.properties set SSL password, for example:
     * 		ducc.ws.port.ssl.pw = quackquack
     *       
     * Note: if SSL port is not set in ducc.properties, the webserver will not create
     *       the SSL connection, and thus secure communications will be unsupported.
	 */
	private int portSsl = -1;
	private String portSslPw = "quackquack";
	private String rootDir = "?";

	//
	
	private Server server;
	
	private CommonConfiguration commonConfiguration;
	
	public DuccWebServer(CommonConfiguration commonConfiguration) {
		this.commonConfiguration = commonConfiguration;
		init();
	}

	public String getJdHostUser() {
		return commonConfiguration.jdHostUser;
	}
	
	/**
	 * The DUCC cluster name can be set in the ducc.properties file, for example:
	 * 		ducc.cluster.name=Watson!
	 */
	public String getClusterName() {
		return commonConfiguration.clusterName;
	}
	
	/**
	 * The DUCC class definition file can be set in the ducc.properties file, for example:
	 * 		ducc.rm.class.definitions = blade.classes
	 */
	public String getClassDefinitionFile() {
		return commonConfiguration.classDefinitionFile;
	}
	
	public int getPortSsl() {
		return portSsl;
	}
	
	public String getRootDir() {
		return rootDir;
	}
	
	private void init() {
		String methodName = "init";
		logger.trace(methodName, null, messages.fetch("enter"));
		logger.info(methodName, null, messages.fetchLabel("cluster name")+getClusterName());
		logger.info(methodName, null, messages.fetchLabel("class definition file")+getClassDefinitionFile());
		if(commonConfiguration.wsIpAddress != null) {
			this.ipaddress = commonConfiguration.wsIpAddress;
			logger.info(methodName, null, messages.fetchLabel("IP Address")+ipaddress);
		}
		if(commonConfiguration.wsPort != null) {
			this.port = Integer.parseInt(commonConfiguration.wsPort);
			logger.info(methodName, null, messages.fetchLabel("port")+port);
		}
		if(commonConfiguration.wsPortSsl != null) {
			this.portSsl = Integer.parseInt(commonConfiguration.wsPortSsl);
			logger.info(methodName, null, messages.fetchLabel("SSL port")+portSsl);
			if(commonConfiguration.wsPortSslPw != null) {
				this.portSslPw = commonConfiguration.wsPortSslPw;
				logger.debug(methodName, null, messages.fetchLabel("SSL pw")+portSslPw);
			}
		}
		try {
			InetAddress inetAddress = InetAddress.getLocalHost();
			String host = inetAddress.getCanonicalHostName();
			DuccWebMonitor.getInstance().register(host, ""+port);
		}
		catch(Exception e) {
			logger.error(methodName, jobid, e);
		}
		String ducc_web_property = System.getProperty("DUCC_WEB");
		String ducc_web_env = System.getenv("DUCC_WEB");
		if(ducc_web_property != null) {
			ducc_web = ducc_web_property;
			logger.info(methodName, null, messages.fetchLabel("DUCC_WEB")+ducc_web);
		}
		else if(ducc_web_env != null) {
			ducc_web = ducc_web_env;
			logger.info(methodName, null, messages.fetchLabel("DUCC_WEB (default)")+ducc_web);
		}
		server = new Server();
		SelectChannelConnector connector0 = new SelectChannelConnector();
        connector0.setPort(port);
        if(ipaddress != null) {
        	connector0.setHost(ipaddress);
        }
        if(portSsl < 0) {
        	server.setConnectors(new Connector[]{ connector0 });
        }
        else {
        	SslSelectChannelConnector ssl_connector = new SslSelectChannelConnector();
        	ssl_connector.setPort(portSsl);
        	if(ipaddress != null) {
            	ssl_connector.setHost(ipaddress);
            }
        	SslContextFactory cf = ssl_connector.getSslContextFactory();
        	cf.setKeyStore(ducc_web + File.separator+"etc"+File.separator+"keystore");
        	cf.setKeyStorePassword(portSslPw);
        	server.setConnectors(new Connector[]{ connector0, ssl_connector });
        }
        //
        ServletContextHandler jspHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        jspHandler.setContextPath("/");
        jspHandler.setResourceBase("root");
        jspHandler.setClassLoader(Thread.currentThread().getContextClassLoader());
        jspHandler.addServlet(DefaultServlet.class, "/");
        ServletHolder jsp = jspHandler.addServlet(JspServlet.class, "*.jsp");
        jsp.setInitParameter("classpath", jspHandler.getClassPath());
        //
		ResourceHandler resourceHandler = new ResourceHandler();
		resourceHandler.setDirectoriesListed(true);
		resourceHandler.setWelcomeFiles(new String[]{ "index.html" });
		rootDir = ducc_web+File.separator+"root";
		resourceHandler.setResourceBase(rootDir);
		//
		try {
			Properties properties = DuccWebProperties.get();
			String ducc_runmode = properties.getProperty("ducc.runmode","Production");
			logger.debug(methodName, null, "ducc.runmode:"+ducc_runmode);
			logger.debug(methodName, null, "rootdir:"+rootDir);
			String $runmode_jsp = rootDir+File.separator+"$banner"+File.separator+"$runmode.jsp";
			logger.debug(methodName, null, "$runmode_jsp:"+$runmode_jsp);
			File $runmodeFile = new File($runmode_jsp);
			logger.debug(methodName, null, "path:"+$runmodeFile.getAbsolutePath());
			$runmodeFile.delete();
			String text;
			if(ducc_runmode.equals("Test")) {
				text = "<html><%@ include file=\"$runmode.test.jsp\" %></html>";
			}
			else {
				text = "<html><%@ include file=\"$runmode.production.jsp\" %></html>";
			}
			PrintWriter out = new PrintWriter($runmodeFile);
			out.println(text);
			out.flush();
			out.close();
		}
		catch(Exception e) {
			logger.info(methodName, null, e);
		}
		//
		HandlerList handlers = new HandlerList();
		DuccHandler duccHandler = new DuccHandler(this);
		DuccHandlerClassic duccHandlerClassic = new DuccHandlerClassic(this);
		DuccHandlerJsonFormat duccHandlerJson = new DuccHandlerJsonFormat(this);
		DuccHandlerProxy duccHandlerProxy = new DuccHandlerProxy();
		DuccHandlerUserAuthentication duccHandlerUserAuthentication = new DuccHandlerUserAuthentication();
		SessionHandler sessionHandler = new SessionHandler();
		handlers.setHandlers(new Handler[] { sessionHandler, duccHandlerUserAuthentication, duccHandlerJson, duccHandlerProxy, duccHandlerClassic, duccHandler, jspHandler, resourceHandler, new DefaultHandler() });
		server.setHandler(handlers);
		logger.trace(methodName, null, messages.fetch("exit"));
	}
	
	public void start() throws Exception {
		server.start();
	}
	
	public void join() throws Exception {
		server.join();
	}
	
	public void stop() throws Exception {
		server.stop();
	}

}

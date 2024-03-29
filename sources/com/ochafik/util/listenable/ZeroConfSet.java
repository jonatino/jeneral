/*
   Copyright 2008 Olivier Chafik

   Licensed under the Apache License, Version 2.0 (the License);
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an AS IS BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   This file comes from the Jalico project (Java Listenable Collections)

       http://jalico.googlecode.com/.
*/
/**
 * 
 */
package com.ochafik.util.listenable;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;

/**
 * Set of <a href="http://www.zeroconf.org/">ZeroConf</a> services discovered over the network.<br/>
 * <img src="zeroconfList.png" alt="Example run of this class, using the iTunes share service type string"/><br/>
 * <br/>
 * Also provides a <a href="#getAdvertisedServiceInfos()">singleton read-write listenable list of advertised services</a> that can be used to announce any service on the local network.<br/>
 * <br/>  
 * Makes use of the <a href="http://jmdns.sourceforge.net/">JmDNS</a> library, that is compatible with Apple's Bonjour technology (former Rendez-Vous).<br/>
 * The constructor of this class takes a service type string as argument. You can look for existing registered service types on the page <a href="http://www.dns-sd.org/ServiceTypes.html">DNS SRV (RFC 2782) Service Types</a>.<br/>
 * Examples :
 * <ul>
 * <li>"_MyTestProtocol._tcp.local." for your tests
 * </li><li>"_daap._tcp.local." for the iTunes music shares discovery
 * </li>
 * </ul>
 * 
 * @author Olivier Chafik
 */
public class ZeroConfSet extends DefaultListenableSet<ServiceInfo> implements ServiceListener {
	String typeString;
	
	@SuppressWarnings("unchecked")
	public ZeroConfSet(final String typeString) {
		super(new TreeSet<ServiceInfo>(new Comparator() {
			String getName(Object o) {
				if (o instanceof ServiceInfo)
					return ((ServiceInfo)o).getName();
				return (String)o;
			}
			public int compare(Object o1, Object o2) {
				return getName(o1).compareTo(getName(o2));
			}
		}));
		this.typeString = typeString;
		getJmDNS().addServiceListener(typeString, this);
		new Thread() { public void run() {
			getJmDNS().requestServiceInfo(typeString, null);
		}}.start();
	}
	
	static ListenableMap<String, ServiceInfo> advertisedServices;
	//static ListenableList<ServiceInfo> advertisedServices;
	
	/**
	 * Read-write listenable list of services advertised by this class.<br/>
	 * To advertise a new service, first build a ServiceInfo instance with all the information you wish.<br/>
	 * Then call ZeroConfListenableMap.getAdvertisedServiceInfos().put(serviceInfo.getName(), serviceInfo).
	 */
	public static ListenableMap<String, ServiceInfo> getAdvertisedServiceInfos() {
		if (advertisedServices == null) {
			advertisedServices = new DefaultListenableMap<String, ServiceInfo>(new HashMap<String, ServiceInfo>());
			advertisedServices.keySet().addCollectionListener(new CollectionListener<String>() {
				Map<String, ServiceInfo> oldValues = new HashMap<String, ServiceInfo>();
				public void collectionChanged(CollectionEvent<String> e) {
					for (String name : e.getElements()) {
						try {
							switch (e.getType()) {
							case UPDATED:
								getJmDNS().unregisterService(oldValues.remove(name));
								
							case ADDED:
								ServiceInfo info = advertisedServices.get(name);
								getJmDNS().registerService(info);
								oldValues.put(name, info);
								break;
							case REMOVED:
								getJmDNS().unregisterService(oldValues.remove(name));
								break;
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			});
			Runtime.getRuntime().addShutdownHook(new Thread() { public void run() {
				advertisedServices.clear();
			}});
		}
		return advertisedServices;
	}
	/*
	public static ListenableList<ServiceInfo> getAdvertisedServiceInfos() {
		if (advertisedServices == null) {
			advertisedServices = new DefaultListenableList<ServiceInfo>(new ArrayList<ServiceInfo>());
			advertisedServices.addCollectionListener(new CollectionListener<ServiceInfo>() {
				//Map<String, ServiceInfo> oldValues = new HashMap<String, ServiceInfo>();
				public void collectionChanged(CollectionEvent<ServiceInfo> e) {
					for (ServiceInfo service : e.getElements()) {
						try {
							switch (e.getType()) {
							case UPDATED:
								getJmDNS().unregisterService(service);
								
							case ADDED:
								//ServiceInfo info = advertisedServices.get(name);
								getJmDNS().registerService(service);
								//oldValues.put(name, info);
								break;
							case REMOVED:
								getJmDNS().unregisterService(service);
								break;
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			});
			Runtime.getRuntime().addShutdownHook(new Thread() { public void run() {
				advertisedServices.clear();
			}});
		}
		return advertisedServices;
	}*/
	
	/**
	 * Stop the listening of services.
	 */
	public synchronized void unregister() {
		getJmDNS().removeServiceListener(typeString, this);
	}
	
	/**
	 * Implementation of the ServiceListener interface
	 */
	public void serviceAdded(final ServiceEvent event) {
		new Thread() { public void run() {
			getJmDNS().requestServiceInfo(typeString, event.getName());
		}}.start();
	}
	
	/**
	 * Implementation of the ServiceListener interface
	 */
	public synchronized void serviceRemoved(final ServiceEvent event) {
		System.out.println("Removing "+event.getName());
		remove(event.getName());
	}
	
	/**
	 * Implementation of the ServiceListener interface
	 */
	public synchronized void serviceResolved(ServiceEvent event) {
		System.out.println("Adding ("+event.getName()+") "+event.getInfo().getName());
		add(event.getInfo());
	}
	
	static JmDNS jmDNS;
	/**
	 * Get a singleton JmDNS instance
	 */
	public static JmDNS getJmDNS() {
		if (jmDNS == null) {
			try {
				jmDNS = new JmDNS();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		return jmDNS;
	}
	
	
	public static void main(String[] args) {
		String key = args.length == 0 ? "_daap._tcp.local." : args[0];
		
		ZeroConfSet set = new ZeroConfSet(key);
		JFrame f = new JFrame(key);
		f.getContentPane().add("Center", 
			new JScrollPane(
				new JList(
					new ListenableListModel<ServiceInfo>(
						ListenableCollections.asList(set)
					)
				)
			)
		);
		f.setSize(700, 400);
		f.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				e.getWindow().setVisible(false);
				if (jmDNS != null) 
					jmDNS.close();
				System.exit(0);
			}
		});
		f.setVisible(true);
		
		Random random = new Random();
		for (int i = 5; i-- != 0;) {
			String name = "Fake iTunes library " + random.nextInt();
			getAdvertisedServiceInfos().put(name, new ServiceInfo(key, name, 11, "Test service string"));
			//getAdvertisedServiceInfos().add(new ServiceInfo(key, name, 11, "Test service string"));
		}
	}
}
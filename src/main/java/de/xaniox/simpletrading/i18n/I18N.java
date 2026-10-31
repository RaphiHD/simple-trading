/*
 * This file is part of SimpleTrading.
 * Copyright (c) 2015-2016 Matthias Werning
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.xaniox.simpletrading.i18n;

import com.google.common.collect.Lists;
import de.xaniox.simpletrading.SimpleTrading;
import de.xaniox.simpletrading.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class I18N {
	
	public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
	private static final List<String> DEFAULT_CLASSPATH_LOCALE_RESOURCES = List.of(
		"locale_en_US.yml", "locale_de_DE.yml", "locale_fr_FR.yml", "locale_it_IT.yml", "locale_ru_RU.yml", "locale_zh_TW.yml");
	private static final String FALLBACK_FILE = "locale_en_US.yml";
	private static final String FILE_PROTOCOL = "file";
	private static final String JAR_EXTENSION = ".jar";
	private static final String JAR_ENTRY_SEPERATOR = "/";
	private static final String LOCALE_FILE_REGEX = "locale_[a-z]{2}(_[A-Z]{2})?\\.yml";
	static final Locale FALLBACK_LOCALE = Locale.US;
	
	private Locale locale;
	private ClassLoader classLoader;
	private Logger logger;
	private LoadingMode mode;
	private YMLControl defaultControl;
	private File fileSystemFolder;
	private String classpathFolder;
	private I18N parent;
	private ResourceBundle bundle;
	
	public I18N() {}
	
	protected I18N(Locale locale, LoadingMode mode, File fileSystemFolder, String classpathFolder, ClassLoader loader, Logger logger) {
		this.defaultControl = new YMLControl(fileSystemFolder, classpathFolder, mode);
		this.locale = locale;
		this.mode = mode;
		this.fileSystemFolder = fileSystemFolder;
		
		if (!classpathFolder.endsWith(String.valueOf(JAR_ENTRY_SEPERATOR)) && !classpathFolder.isEmpty()) {
			classpathFolder += JAR_ENTRY_SEPERATOR;
		}
		
		this.classpathFolder = classpathFolder;
		this.classLoader = loader;
		this.logger = logger;
		
		load();
	}
	
	public void load() {
		if (mode == LoadingMode.FILE_SYSTEM) {
			//Copy those resource files when we're using the file system loading mode
			try {
				copyClasspathResources();
			} catch (IOException e) {
				// Just inform as the YMLControl is going to load this file from the classpath
				logger.log(Level.WARNING, "Could not copy locale resource file, using classpath resource", e);
			}
		}
		
		loadBundle();
	}
	
	public void setLocale(Locale locale) {
		this.locale = locale;
		
		loadBundle();
	}
	
	private void loadBundle() {
		bundle = ResourceBundle.getBundle("locale", locale, defaultControl);
	}
	
	public void setParent(I18N parent) {
		this.parent = parent;
	}
	
	public void copyClasspathResources() throws IOException {
		if (mode != LoadingMode.FILE_SYSTEM) {
			throw new IllegalStateException("Resource files can only be copied on LoadingMode.FILE_SYSTEM mode");
		}
		
		//Try to find a list of all available resources
		List<String> classpathResources = Lists.newArrayList();
		if (classLoader instanceof URLClassLoader) {
			URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
			URL[] urls = urlClassLoader.getURLs();
			
			//Get a list of all locale resources in this URLClassLoader
			for (URL url : urls) {
				if (!url.getProtocol().equals(FILE_PROTOCOL)) {
					//This url doesn't point to a file
					continue;
				}
				
				File file;
				
				try {
					file = new File(url.toURI());
				} catch (URISyntaxException e) {
					//Shouldn't fire as the classloader should have already
					//validated the url
					throw new RuntimeException(e);
				}
				
				if (!file.getName().endsWith(JAR_EXTENSION)) {
					//This file isn't a jar archive
					continue;
				}
				
				try (JarFile jar = new JarFile(file)) {
					Enumeration<JarEntry> entries = jar.entries();
					
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						String entryName = entry.getName();
						
						String[] nameComponents = entryName.split(JAR_ENTRY_SEPERATOR);
						StringBuilder folderBuilder = new StringBuilder();
						for (int i = 0; i < nameComponents.length - 1; i++) {
							folderBuilder.append(nameComponents[i])
										.append(JAR_ENTRY_SEPERATOR);
						}
						
						String folder = folderBuilder.toString();
						
						if (!folder.equals(classpathFolder)) {
							//This resource isn't in the right directory
							continue;
						}
						
						String fileName = nameComponents[nameComponents.length - 1];
						if (!fileName.matches(LOCALE_FILE_REGEX)) {
							//This entry isn't a resource entry
							continue;
						}
						
						//Found a resource file
						classpathResources.add(fileName);
					}
				}
			}
		} else {
			classpathResources.addAll(DEFAULT_CLASSPATH_LOCALE_RESOURCES);
		}
		
		for (String localeRes : classpathResources) {
			File localeFile = new File(fileSystemFolder, localeRes);
			
			if (!localeFile.exists()) {
				URL localeResourceUrl = classLoader.getResource(classpathFolder + localeRes);
				
				SimpleTrading.copyResource(localeResourceUrl, localeFile);
			}
		}
		
		// Check if all messages exist, and add missing messages to the file e.g validate and replace
		File[] localeFiles = fileSystemFolder.listFiles();
		if (localeFiles == null) {
			return;
		}
		
		for (File localeFile : localeFiles) {
			//Check if the file is a localization file
			if (!localeFile.getName().matches(LOCALE_FILE_REGEX)) {
				continue;
			}
			
			//Classpath resources can only exist in one folder, so their name is unique
			URL classpathResource = classLoader.getResource(classpathFolder + localeFile.getName());
			if (classpathResource == null) {
				classpathResource = classLoader.getResource(classpathFolder + FALLBACK_FILE);
			}
			
			URLConnection connection = classpathResource.openConnection();
			connection.setUseCaches(false);
			
			StringBuilder fileBuilder;
			StringBuilder resourceBuilder;
			
			try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(localeFile), UTF8_CHARSET));
				 BufferedReader resourceReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), UTF8_CHARSET))) {
				
				fileBuilder = new StringBuilder();
				resourceBuilder = new StringBuilder();
				
				String read;
				while ((read = fileReader.readLine()) != null) {
					fileBuilder.append(read).append('\n');
				}
				
				while ((read = resourceReader.readLine()) != null) {
					resourceBuilder.append(read).append('\n');
				}
			}
			
			YamlConfiguration fileConfig = new YamlConfiguration();
			YamlConfiguration resourceConfig = new YamlConfiguration();
			
			try {
				fileConfig.loadFromString(fileBuilder.toString());
				resourceConfig.loadFromString(resourceBuilder.toString());
			} catch (InvalidConfigurationException e) {
				logger.log(Level.SEVERE, "Could not validate " + localeFile.getName() + ", ignoring file", e);
				continue;
			}
			
			boolean changesToCommit = false;
			
			for (String key : resourceConfig.getKeys(true)) {
				if (resourceConfig.isConfigurationSection(key)) {
					continue;
				}
				
				if (!fileConfig.contains(key)) {
					fileConfig.set(key, resourceConfig.get(key));
					changesToCommit = true;
				}
			}
			
			if (changesToCommit) {
				// YamlConfiguration always saves files as UTF-8
				fileConfig.save(localeFile);
			}
		}
	}
	
	public String getString(String key) {
		String msg;
		
		synchronized (bundle) {
			try {
				msg = bundle.getString(key);
			} catch (MissingResourceException e) {
				if (parent != null) {
					msg = parent.getString(key);
				} else {
					throw e;
				}
			}
		}
		
		return msg;
	}
	
	/**
	 * Returns the message as a component, parsing its legacy {@code &} color codes
	 */
	public Component get(String key) {
		return Text.of(getString(key));
	}
	
	public ParsedMessage getVarString(String key) {
		String message = getString(key);
		
		try {
			return ParsedMessage.parseMessage(message);
		} catch (ParseException e) {
			//Report the exception
			logger.log(Level.SEVERE, "Illegal message \"" + message + "\"", e);
			
			//Return something to prevent expections
			List<ParsedMessage.MessageVariable> emptySet = Lists.newArrayList();
			return new ParsedMessage(message, emptySet);
		}
	}
	
	public String[] getStringArray(String key) {
		String[] array;
		
		synchronized (bundle) {
			try {
				array = bundle.getStringArray(key);
			} catch (MissingResourceException e) {
				if (parent != null) {
					array = parent.getStringArray(key);
				} else {
					throw e;
				}
			}
		}
		
		return array;
	}
	
	public enum LoadingMode {
		
		CLASSPATH,
		FILE_SYSTEM;
		
	}
	
}
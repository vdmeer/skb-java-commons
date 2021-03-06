/* Copyright 2014 Sven van der Meer <vdmeer.sven@mykolab.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.vandermeer.skb.commons.utils;

import java.net.URL;
import java.util.Collection;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vandermeer.skb.base.categories.IsFactory;
import de.vandermeer.skb.base.categories.IsPath;
import de.vandermeer.skb.base.categories.OfGroup;
import de.vandermeer.skb.base.composite.coin.CC_Error;
import de.vandermeer.skb.base.info.FileSource;
import de.vandermeer.skb.base.info.PropertyFileLoader;
import de.vandermeer.skb.base.message.Message5WH_Builder;
import de.vandermeer.skb.base.tools.ReportManager;
import de.vandermeer.skb.base.utils.collections.SetStrategy;
import de.vandermeer.skb.commons.collections.FlatMultiTree;
import de.vandermeer.skb.commons.collections.PropertyTable;
import de.vandermeer.skb.commons.collections.Tree;
import de.vandermeer.skb.configuration.EAttributeKeys;
import de.vandermeer.skb.configuration.EPath;
import de.vandermeer.skb.configuration.EPropertyKeyGroups;
import de.vandermeer.skb.configuration.EPropertyKeys;

/**
 * Context factory.
 *
 * @author     Sven van der Meer &lt;vdmeer.sven@mykolab.com&gt;
 * @version    v0.0.4 build 150701 (01-Jul-15) for Java 1.8
 */
public enum SkbContextFactory implements IsFactory {
	/** Enumerate 'get' realizing the singleton pattern for the factory. */
	get;

	final Logger logger = LoggerFactory.getLogger(SkbContextFactory.class);

	/** Default property pointing to a configuration file. */
	public static final String CONFIG_FILE_PROPERTY = "de.vandermeer.skb.context.jsonfile";

	/** Default configuration file. */
	public static final String CONFIG_FILE_DEFAULT = "de/vandermeer/skb/commons/context.properties";

	/** Last error message. */
	public final CC_Error lastError = new CC_Error();

	/**
	 * Loads and returns default properties.
	 * @return a {@link Tree} with property information or null (errors are in lastError)
	 */
	public Tree<?> defaultProperties(){
		PropertyFileLoader pfl = new PropertyFileLoader(SkbContextFactory.CONFIG_FILE_DEFAULT);
		Properties properties = pfl.load();
		//TODO send errors to logger
		this.lastError.add(pfl.getLoadErrors());

		String propFile = properties.getProperty(SkbContextFactory.CONFIG_FILE_PROPERTY);
		return this.propertiesFromFile(propFile, EPath.CONFIGURATION);
	}

	/**
	 * Loads and returns properties from specified file and path.
	 * @param fileName file name, will be tried as resource first
	 * @param path path in the file for information
	 * @return a {@link Tree} with property information or null (errors are in lastError)
	 */
	public Tree<?> propertiesFromFile(Object fileName, IsPath path){
		this.lastError.clear();

		//check if property contains a string, warning if empty
		if(fileName==null){
			logger.warn("empty filename <{}>, no configuration loaded", fileName);
			this.lastError.add(new Message5WH_Builder().addWhat("empty property").addHow(fileName).build());
			return null;
		}

		FileSource fs = new FileSource(fileName.toString());
		URL url = fs.asURL();
		if(url==null){
			logger.warn("could not read file <{}>, tried as resource and as file name", fileName);
			this.lastError.add(new Message5WH_Builder().addWhat("error loading file from resource and file system").addHow(fileName).build());
			return null;
		}

		//read a JSON string from the file, warning if problems with parsing (null) or if return is not a tree (not valid info)
		Object cc = new Json2Collections().read(url);
		if(cc==null){
			logger.warn("JSON conversion returned null");
			this.lastError.add(new Message5WH_Builder().addWhat("problem parsing JSON file").addHow(fileName).build());
			return null;
		}
		if(!(cc instanceof Tree)){
			logger.warn("expected tree, found <{}>", cc.getClass().getSimpleName());
			this.lastError.add(new Message5WH_Builder().addWhat("wrong type").addHow("expected tree, found ", cc.getClass().getSimpleName()).build());
			return null;
		}

		//check if the tree actually contains configuration (must have the path given by the enum)
		Tree<?> ret = ((Tree<?>)cc).getSubtree(path.path());
		if(ret==null){
			logger.warn("no configuration information in configuration file");
			this.lastError.add(new Message5WH_Builder().addWhat("no information found").addHow("no configuration information in file <", fileName, "> for path <", path, ">").build());
			return null;
		}
		return ret;
	}

	/**
	 * Reads properties of a given path from files.
	 * @param files array of files to parse
	 * @param path path for property information in each file
	 * @return a tree with all found properties
	 */
	public Tree<?> propertiesFromFile(Object[] files, IsPath path){
		FlatMultiTree<Object> ret = new FlatMultiTree<Object>();
		CC_Error errors = new CC_Error();

		if(files!=null){
			for(Object file : files){
				Tree<?> add = this.propertiesFromFile(file, path);
				if(add!=null){
					ret.merge(add);
				}
				else{
					errors.add(this.lastError);
				}
			}
		}
		this.lastError.add(errors);
		return ret;
	}

	/**
	 * Returns a property table with properties loaded from the given tree.
	 * @param input tree with property information
	 * @return new property table
	 */
	public PropertyTable newPropertyTable(Object input){
		PropertyTable ret = new PropertyTable(SetStrategy.HASH_SET);
		if(input!=null && input instanceof Tree<?>){
			Tree<?> tree = (Tree<?>)input;
			Collection<OfGroup> autoRows = OfGroup.GET_KEYS_FOR_GROUP(EPropertyKeyGroups.AUTOMATIC_PROPERTY_ROW, EPropertyKeys.values());

			ret.addRowsAll(autoRows);

			ret.columnValue(EPropertyKeys.APPLICATION_DIRECTORY, EAttributeKeys.VALUE_DEFAULT, System.getProperty("user.dir")+System.getProperty("file.separator"));

			ret.loadFromTree(EPath.CONFIGURATION, autoRows, tree);
		}
		return ret;
	}

	/**
	 * Returns a fully configured report manager.
	 * @param props properties with information for the report manager's string template file.
	 * @return new report manager
	 */
	public ReportManager newReportManager(PropertyTable props){
		if(props==null){
			return null;
		}
		return new ReportManager(props.getPropertyValue(EPropertyKeys.CLI_OPTION_REPORTMGR_STG), "context");
	}

	/**
	 * Reloads the report manager.
	 * @param arm report manager to reload
	 * @param props properties to use for reload
	 * @return reloaded report manager
	 */
	public boolean reloadReportManager(ReportManager arm, PropertyTable props){
		if(arm==null || props==null){
			return false;
		}
		arm.loadStgFromFile(props.getPropertyValue(EPropertyKeys.CLI_OPTION_REPORTMGR_STG), "context");
		return true;
	}
}

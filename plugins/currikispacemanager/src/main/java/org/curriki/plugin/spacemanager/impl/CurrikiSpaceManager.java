package org.curriki.plugin.spacemanager.impl;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.XWikiPluginInterface;
import com.xpn.xwiki.api.Api;
import org.xwiki.plugin.spacemanager.api.Space;
import org.xwiki.plugin.spacemanager.api.SpaceManagerException;
import org.xwiki.plugin.spacemanager.api.SpaceManagerExtension;
import org.xwiki.plugin.spacemanager.impl.SpaceManagerImpl;
import org.curriki.plugin.spacemanager.plugin.CurrikiSpaceManagerPluginApi;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ludovic
 * Date: 14 d�c. 2007
 * Time: 09:23:04
 * To change this template use File | Settings | File Templates.
 */
public class CurrikiSpaceManager extends SpaceManagerImpl {
    private static final String CURRIKI_SPACEMANAGER_DEFAULT_EXTENSION = "org.xwiki.plugin.spacemanager.impl.CurrikiSpaceManagerExtension";

    /**
	 * Space manager constructor
	 * @param name
	 * @param className
	 * @param context
	 */
    public CurrikiSpaceManager(String name, String className, XWikiContext context)
    {
        super(name, className, context);
    }

    public String getName() {
        return "csm";
    }

    /**
	* Loads the CurrikiSpaceManagerExtension specified in the config file
	* @return Returns the space manager extension
	 * @throws SpaceManagerException
	*/
	public SpaceManagerExtension getSpaceManagerExtension(XWikiContext context) throws SpaceManagerException
	{
        if (spaceManagerExtension==null) {
            String extensionName = context.getWiki().Param(SPACEMANAGER_EXTENSION_CFG_PROP,CURRIKI_SPACEMANAGER_DEFAULT_EXTENSION);
            try {
                if (extensionName!=null)
                 spaceManagerExtension = (SpaceManagerExtension) Class.forName(extensionName).newInstance();
            } catch (Throwable e){
                try{
                    spaceManagerExtension = (SpaceManagerExtension) Class.forName(CURRIKI_SPACEMANAGER_DEFAULT_EXTENSION).newInstance();
                } catch(Throwable  e2){
                }
            }
        }

        if (spaceManagerExtension==null) {
            spaceManagerExtension = new CurrikiSpaceManagerExtension();
        }

        return spaceManagerExtension;
    }

    /**
     * Gets the space plugin Api
     * @param plugin The plugin interface
     * @param context Xwiki context
     * @return
     */
    public Api getPluginApi(XWikiPluginInterface plugin, XWikiContext context) {
        return new CurrikiSpaceManagerPluginApi((CurrikiSpaceManager) plugin, context);
    }


    protected Space newSpace(String spaceName, String spaceTitle, boolean create, XWikiContext context) throws SpaceManagerException {
        return new CurrikiSpace(spaceName, spaceTitle, create, this, context);    //To change body of overridden methods use File | Settings | File Templates.
    }

    protected String getCurrikiSpaceClassName() {
        return ((CurrikiSpaceManagerExtension) getSpaceManagerExtension()).getCurrikiSpaceClassName();
    }

    public List getSpacesByTopic(String topic, int nb, int start, XWikiContext context) throws SpaceManagerException {
        String currikiClassName = getCurrikiSpaceClassName();
        String fromhql = ", BaseObject as cobj, DBStringListProperty as lprop";
        String wheresql = " and doc.fullName=cobj.name and cobj.className='" + currikiClassName
                        + "' and cobj.id=lprop.id.id and lprop.id.name='" + CurrikiSpace.SPACE_TOPIC + "' and '" + topic + "' in elements(lprop.list)" ;
        return searchSpaces(fromhql, wheresql, nb, start, context);
    }

    public List getSpaceNamesByTopic(String topic, int nb, int start, XWikiContext context) throws SpaceManagerException {
        String currikiClassName = getCurrikiSpaceClassName();
        String fromhql = ", BaseObject as cobj, DBStringListProperty as lprop";
        String wheresql = " and doc.fullName=cobj.name and cobj.className='" + currikiClassName
                        + "' and cobj.id=lprop.id.id and lprop.id.name='" + CurrikiSpace.SPACE_TOPIC + "' and '" + topic + "' in elements(lprop.list)" ;
        return searchSpaceNames(fromhql, wheresql, nb, start, context);
    }

    public List countSpacesByTopic(String parentTopic, XWikiContext context) throws SpaceManagerException {
        String type = getSpaceTypeName();
        String className = getSpaceClassName();
        String currikiClassName = getCurrikiSpaceClassName();
        String sql;
        String parentfromsql = (parentTopic==null) ? "" : ", XWikiDocument as doc2";
        String parentsql = (parentTopic==null) ? "" : " and doc2.fullName = topic.id and doc2.parent='" + parentTopic + "'";
        if (hasCustomMapping())
            sql = "select topic.id, count(*) from XWikiDocument as doc, BaseObject as obj, " + className + " as space, BaseObject as cobj, DBStringListProperty as lprop join lprop.list as topic" + parentfromsql
                 + " where doc.fullName = obj.name and obj.className='" + className + "' and obj.id = space.id and space.type='" + type + "'"
                 + " and doc.fullName=cobj.name and cobj.className='" + currikiClassName
                 + "' and cobj.id=lprop.id.id and lprop.id.name='" + CurrikiSpace.SPACE_TOPIC + "'" + parentsql + " group by 1" ;
        else
            sql = "select topic.id, count(*) from XWikiDocument as doc, BaseObject as obj, StringProperty as typeprop, BaseObject as cobj, DBStringListProperty as lprop join lprop.list as topic" + parentfromsql
                 + " where doc.fullName=obj.name and obj.className = '" + className + "' and obj.id=typeprop.id.id and typeprop.id.name='type' and typeprop.value='" + type + "'"
                    + " and doc.fullName=cobj.name and cobj.className='" + currikiClassName
                    + "' and cobj.id=lprop.id.id and lprop.id.name='" + CurrikiSpace.SPACE_TOPIC + "'" + parentsql + " group by 1" ;
                      
        try {
            return context.getWiki().search(sql, context);
        } catch (XWikiException e) {
            throw new SpaceManagerException(e);
        }
    }

}

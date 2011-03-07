/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.curriki.xwiki.plugin.asset;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.collections.ListUtils;
import org.apache.velocity.VelocityContext;
import org.curriki.xwiki.plugin.asset.attachment.*;
import org.curriki.xwiki.plugin.asset.composite.CollectionCompositeAsset;
import org.curriki.xwiki.plugin.asset.composite.FolderCompositeAsset;
import org.curriki.xwiki.plugin.asset.composite.RootCollectionCompositeAsset;
import org.curriki.xwiki.plugin.asset.other.InvalidAsset;
import org.curriki.xwiki.plugin.asset.other.ProtectedAsset;
import org.curriki.xwiki.plugin.asset.text.TextAssetManager;
import org.curriki.xwiki.plugin.asset.text.TextAsset;
import org.curriki.xwiki.plugin.mimetype.MimeTypePlugin;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.fileupload.FileUploadPlugin;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.api.Property;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.XWikiLock;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseStringProperty;
import com.xpn.xwiki.api.Object;
import com.xpn.xwiki.api.Attachment;

public class Asset extends CurrikiDocument {
    private static final Log LOG = LogFactory.getLog(Asset.class);


    public Asset(XWikiDocument doc, XWikiContext context) {
        super(doc, context);
    }


    /**
     * Provide a title valid to insert in a javascript call in a tag onclick
     * @return
     */
    public String getJSTitle() {
        return Util.escapeForJS(getDisplayTitle());
    }

    /**
     * Provide a title valid to insert in a javascript call in a tag onclick
     * with a title length limitation
     * @param length
     * @return
     */
    public String getJSTitle(int length) {
        return Util.escapeForJS(getDisplayTitle(length));
    }

    /**
     * Provide the document fullname escaped for inclusing in a js call in a tab onclick
     * @return
     */
    public String getJSFullName() {
        return Util.escapeForJS(getFullName());
    }

    /**
     * Provided a truncated title to the length specified
     * @param length
     * @return
     */
    public String getDisplayTitle(int length) {
        String title = getDisplayTitle();
        if (title.length()>length) {
         title = title.substring(0,length);
         title = title + "...";
        }
        return title;
    }


    /**
     * Return a display call without the {pre} tags to be usable in plain vm templates
     * @param fieldname
     * @param mode
     * @param nopre
     * @return
     */
    public String display(String fieldname, String mode, boolean nopre) {
        String result = super.display(fieldname, mode);
        if (nopre) {
            return result.replaceAll("\\{/?pre\\}","");
        } else
            return result;
    }

    public String getCategory() {
        if (hasA(Constants.ASSET_CLASS)) {
            Object obj = getObject(Constants.ASSET_CLASS);
            use(obj);
            String category = (String) getValue(Constants.ASSET_CLASS_CATEGORY);
            if (category==null)
                return Constants.ASSET_CATEGORY_UNKNOWN;
            else
                return category;
        } else {
            return Constants.ASSET_CATEGORY_UNKNOWN;
        }
    }

    public String getFiletype() {
        String className = getActiveClass();
        try {
            use(Constants.ATTACHMENT_ASSET_CLASS);
            return (String) getValue(Constants.ATTACHMENT_ASSET_FILE_TYPE);
        } finally {
            if (className!=null)
                use(className);
        }
    }

    public String getFiletypeClass() {
        String filetype = getFiletype();
        if ((filetype==null)||("".equals(filetype)))
            return "unknown";
        else
            return filetype;
    }

    public String getCategoryClass() {
        String className = getActiveClass();
        String category = getCategory();

        if (Constants.ASSET_CATEGORY_COLLECTION.equals(category)) {
            try {
                use(Constants.COMPOSITE_ASSET_CLASS);
                String colltype = (String) getValue(Constants.COMPOSITE_ASSET_CLASS_TYPE);
                // in case it is a folder we want folder and not collection
                if (Constants.COMPOSITE_ASSET_CLASS_TYPE_SUBFOLDER.equals(colltype)) {
                    return "folder";
                }
            } finally {
                if (className!=null)
                    use(className);
            }
        }

        if (Constants.ASSET_CATEGORY_ATTACHMENT.equals(category)) {
            // it the attachment is unknown or empty we want "attachment-unknown" and not "attachment"
            if ("unknown".equals(getFiletypeClass()))
                return "attachment-unknown";
        }

        if ((category==null)||("".equals(category)))
            return "unknown";
        else
            return category;
    }

    public void setCategory(String category) {
        BaseObject obj = getDoc().getObject(Constants.ASSET_CLASS, true, context);
        obj.setStringValue(Constants.ASSET_CLASS_CATEGORY, category);
    }

    /**
     * Gets the category sub-type (file type in the case of attachments)
     *
     * This should be overridden in each of the sub-classes of Asset
     *
     * @return category sub-type
     */
    public String getCategorySubtype() {
        Asset asset;

        try {
            asset = subclassAs(getAssetClass());
            // If we didn't get a subtype then return unknown subtype
            if (asset.getClass().getSimpleName().equals("Asset")) {
                return Constants.ASSET_CATEGORY_SUBTYPE_UNKNOWN;
            }
            return asset.getCategorySubtype();
        } catch (XWikiException e) {
            return Constants.ASSET_CATEGORY_SUBTYPE_UNKNOWN;
        }
    }

    /**
     * This functions will display the asset including a fallback system
     * For a specific mode. This function can be overidden for a specific asset type
     * Otherwise it will use a default rule system to find the appropriate template
     * @return
     */
    public String displayAsset(String mode) {
        String result = "";
        Asset asset = null;

        // we should subclass the asset to have access
        // to more functions
        try {
            asset = subclassAs(getAssetClass());
        } catch (XWikiException e) {
            asset = this;
        }

        java.lang.Object previousAsset = null;
        VelocityContext vcontext = null;
        try {
            vcontext = (VelocityContext) context.get("vcontext");
            previousAsset = vcontext.get("asset");
            vcontext.put("asset", asset);
            // run the displayer
            result = asset.displayAssetTemplate(mode);
        } finally {
            if (vcontext !=null) {
                if (previousAsset==null)
                    vcontext.remove("asset");
                else
                    vcontext.put("asset", previousAsset);
            }
        }
        return result;
    }

    /**
     * This functions will display the asset including a fallback system
     * For a specific mode. This function can be overidden for a specific asset type
     * Otherwise it will use a default rule system to find the appropriate template
     * @return
     */
    protected String displayAssetTemplate(String mode) {
        MimeTypePlugin mimePlugin = getMimeTypePlugin();
        String category = getCategory();
        String displayer = mimePlugin.getDisplayer(category, null, context);
        String result = context.getWiki().parseTemplate("assets/displayers/" + displayer  + "_" + mode + ".vm", context);
        if (result.equals(""))
           result =  context.getWiki().parseTemplate("assets/displayers/" + category + "_" + mode + ".vm", context);
        if (result.equals(""))
           result =  context.getWiki().parseTemplate("assets/displayers/" + mode + ".vm", context);
        return result;
    }
        
    public void saveDocument(String comment) throws XWikiException {
        saveDocument(comment, false);
    }

    public static Asset createTempAsset(String parentAsset, XWikiContext context) throws XWikiException {
        return createTempAsset(parentAsset, null, context);
    }

    public static Asset createTempAsset(String parentAsset, String publishSpace, XWikiContext context) throws XWikiException {
        if (Constants.GUEST_USER.equals(context.getUser())) {
            throw new AssetException(AssetException.ERROR_ASSET_FORBIDDEN, "XWikiGuest cannot create assets.");
        }

        String pageName = context.getWiki().getUniquePageName(Constants.ASSET_TEMPORARY_SPACE, context);

        XWikiDocument newDoc = context.getWiki().getDocument(Constants.ASSET_TEMPORARY_SPACE, pageName, context);

        Asset assetDoc = new Asset(newDoc, context);
        assetDoc.init(parentAsset, publishSpace);
        assetDoc.saveDocument(context.getMessageTool().get("curriki.comment.createnewsourceasset"), true);

        return assetDoc;
    }

    public static Asset copyTempAsset(String copyOf, XWikiContext context) throws XWikiException {
        return copyTempAsset(copyOf, null, context);
    }

    public static Asset copyTempAsset(String copyOf, String publishSpace, XWikiContext context) throws XWikiException {
        if (Constants.GUEST_USER.equals(context.getUser())) {
            throw new AssetException(AssetException.ERROR_ASSET_FORBIDDEN, "XWikiGuest cannot create assets.");
        }

        Asset copyDoc = fetchAsset(copyOf, context);
        if (!copyDoc.assertCanDuplicate()) {
            throw new AssetException(AssetException.ERROR_ASSET_FORBIDDEN, "Source resource cannot be copied");
        }


        String pageName = context.getWiki().getUniquePageName(Constants.ASSET_TEMPORARY_SPACE, context);

        XWikiDocument newDoc = copyDoc.getDoc().copyDocument(Constants.ASSET_TEMPORARY_SPACE+"."+pageName, context);

        Asset assetDoc = new Asset(newDoc, context);
        //assetDoc.init(copyOf, publishSpace);
        assetDoc.getDoc().setCreator(context.getUser());
        assetDoc.getDoc().setCustomClass(assetDoc.getClass().getName());

        // Remove comments from copied asset
        assetDoc.removeObjects("XWiki.XWikiComments");

        // Remove inherited CRS-reviews from copied asset
        assetDoc.removeObjects(Constants.ASSET_CURRIKI_REVIEW_CLASS);
        assetDoc.removeObjects(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS);

        BaseObject newLicenceObj = assetDoc.getDoc().getObject(Constants.ASSET_LICENCE_CLASS);
        if (newLicenceObj==null) {
            newLicenceObj = assetDoc.getDoc().newObject(Constants.ASSET_LICENCE_CLASS, context);
        }

        // Rights Holder should be by default the pretty name of the user added with the current rights holder (only if not already in the list)
        String newRightsHolder = context.getWiki().getLocalUserName(context.getUser(), null, false, context);
        String origRightsHolder = copyDoc.getDoc().getStringValue(Constants.ASSET_LICENCE_CLASS, Constants.ASSET_LICENCE_ITEM_RIGHTS_HOLDER);
	if (!origRightsHolder.matches("\\b"+newRightsHolder+"\\b")) {
		newRightsHolder += ", " + origRightsHolder;
		newLicenceObj.setStringValue(Constants.ASSET_LICENCE_ITEM_RIGHTS_HOLDER, newRightsHolder);
	} else {
		newLicenceObj.setStringValue(Constants.ASSET_LICENCE_ITEM_RIGHTS_HOLDER, origRightsHolder);
	}

        BaseObject newObjAsset = assetDoc.getDoc().getObject(Constants.ASSET_CLASS);
        if (newObjAsset==null) {
            newObjAsset = assetDoc.getDoc().newObject(Constants.ASSET_CLASS, context);
        }
        // Keep the information allowing to track where that asset came from
        newObjAsset.setStringValue(Constants.ASSET_CLASS_TRACKING, copyOf);


        // Clear rights objects otherwise this will trigger a remove object although these have never been saved
        assetDoc.getDoc().setObjects("XWiki.XWikiRights", new Vector<BaseObject>());

        assetDoc.applyRightsPolicy();

        //assetDoc.saveDocument(context.getMessageTool().get("curriki.comment.copiedsourceasset"), true);
        context.getWiki().saveDocument(assetDoc.getDoc(), context.getMessageTool().get("curriki.comment.copiedsourceasset"), true, context);
        return assetDoc;
    }

    protected void init(String parentAsset) throws XWikiException {
        init(parentAsset, null);
    }

    protected void init(String parentAsset, String publishSpace) throws XWikiException {
        assertCanEdit();
        getDoc().setCreator(context.getUser());

        inheritMetadata(parentAsset, publishSpace);

        getDoc().setCustomClass(getClass().getName());
        setDefaultContent();

        applyRightsPolicy();
    }

    protected void setDefaultContent() throws XWikiException {
        assertCanEdit();
        getDoc().setContent("");
    }

    protected void initSubType() throws XWikiException {
        assertCanEdit();
        // Empty for Superclass
    }

    public void addAttachment(InputStream iStream, String name) throws XWikiException, IOException {
        assertCanEdit();
        XWikiAttachment att = addAttachment(name, iStream).getAttachment();
        getDoc().saveAttachmentContent(att, context);
    }

    public String getDisplayTitle() {
        String title = getTitle();
        return (title == null || title.length() == 0) ? Constants.ASSET_DISPLAYTITLE_UNTITLED : title;
    }

    public String getDescription() {
        String className = getActiveClass();

        use(Constants.ASSET_CLASS);
        String description = (String) getValue(Constants.ASSET_CLASS_DESCRIPTION);

        if (className != null) {
            use(className);
        }

        return (description == null || description.length() == 0) ? "" : description;
    }

    public void changeOwnership(String newUser) {
        if (hasProgrammingRights()) {
            XWikiDocument assetDoc = getDoc();
            assetDoc.setCreator(newUser);
        }
    }

    /**
     * Set the rights objects based on the current right setting
     *
     * @throws XWikiException
     */
    public void applyRightsPolicy() throws XWikiException {
        applyRightsPolicy(null);
    }

    /**
     * Set the rights object based on the right in param or the current right setting if null
     *
     * @param right
     * @throws XWikiException
     */
    public void applyRightsPolicy(String right) throws XWikiException {
        XWikiDocument assetDoc = getDoc();
        assetDoc.removeObjects("XWiki.XWikiRights");

        BaseObject assetObj = assetDoc.getObject(Constants.ASSET_CLASS);
        String rights;

        if (right == null) {
            // Use existing rights value
            rights = assetObj.getStringValue(Constants.ASSET_CLASS_RIGHT);
            LOG.debug("applyRightsPolicy:  retrieved: '"+right+"'");
        } else {
            LOG.debug("applyRightsPolicy:  passed: '"+right+"'");
            rights = right;
            assetObj.setStringValue(Constants.ASSET_CLASS_RIGHT, right);
        }

        // Make sure rights value is valid, default to PUBLIC if not
        if (rights == null
            || !(rights.equals(Constants.ASSET_CLASS_RIGHT_PUBLIC)
                 || rights.equals(Constants.ASSET_CLASS_RIGHT_MEMBERS)
                 || rights.equals(Constants.ASSET_CLASS_RIGHT_PRIVATE))) {
            LOG.warn("Rights is being defaulted.  Got: '"+rights+"'");
            rights = Constants.ASSET_CLASS_RIGHT_PUBLIC;
            assetObj.setStringValue(Constants.ASSET_CLASS_RIGHT, rights);
        }

        // Rights
        BaseObject rightObj;

        // If collection is user
        String usergroupfield = Constants.RIGHTS_CLASS_USER;
        String uservalue =  ("".equals(assetDoc.getCreator())) ? context.getUser() : assetDoc.getCreator();
        String usergroupvalue = uservalue;

        // If collection is group
        if (assetDoc.getSpace().startsWith(Constants.GROUP_COLLECTION_SPACE_PREFIX)) {
            usergroupfield = Constants.RIGHTS_CLASS_GROUP;
            usergroupvalue = assetDoc.getSpace().substring(5) + ".MemberGroup";
        }

        // Always let the admin group have edit access
        rightObj = assetDoc.newObject(Constants.RIGHTS_CLASS, context);
        rightObj.setLargeStringValue(Constants.RIGHTS_CLASS_GROUP, Constants.RIGHTS_ADMIN_GROUP);
        rightObj.setStringValue("levels", "edit");
        rightObj.setIntValue("allow", 1);

        // Always let the creator/group edit
        rightObj = assetDoc.newObject(Constants.RIGHTS_CLASS, context);
        // CURRIKI-2468 - allow creator to always edit/view their creations
        if (usergroupfield.equals(Constants.RIGHTS_CLASS_GROUP)) {
            rightObj.setLargeStringValue(Constants.RIGHTS_CLASS_USER, uservalue);
        }
        rightObj.setLargeStringValue(usergroupfield, usergroupvalue);
        rightObj.setStringValue("levels", "edit");
        rightObj.setIntValue("allow", 1);

        if (rights.equals(Constants.ASSET_CLASS_RIGHT_PUBLIC)) {
            // Viewable by all and any member can edit
            rightObj = assetDoc.newObject(Constants.RIGHTS_CLASS, context);
            rightObj.setLargeStringValue(Constants.RIGHTS_CLASS_GROUP, Constants.RIGHTS_ALL_GROUP);
            rightObj.setStringValue("levels", "edit");
            rightObj.setIntValue("allow", 1);
        } else if (rights.equals(Constants.ASSET_CLASS_RIGHT_MEMBERS)) {
            // Viewable by all, only user/group can edit
        } else {
            // rights == private, so only allow creator/group to view and edit
            rightObj = assetDoc.newObject(Constants.RIGHTS_CLASS, context);
            // CURRIKI-2468 - allow creator to always edit/view their creations
            if (usergroupfield.equals(Constants.RIGHTS_CLASS_GROUP)) {
                rightObj.setLargeStringValue(Constants.RIGHTS_CLASS_USER, uservalue);
            }
            rightObj.setLargeStringValue(usergroupfield, usergroupvalue);
            rightObj.setStringValue("levels", "view");
            rightObj.setIntValue("allow", 1);

            // TODO: something here caused CURRIKI-3100 so the rest of the metadata could not get saved
            //private assets must be removed from review queue
            removeFromReviewQueue();
        }
    }

    public Class<? extends Asset> getAssetClass() {
        if (hasAccessLevel("view")) {
            if (isFolder()){
                if (isRootCollection()){
                    // Note that the Root Collection doesn't have an Asset Class
                    //  so it has to come before checking if no ASSET_CLASS
                    return RootCollectionCompositeAsset.class;
                } else if (isCollection()){
                    return CollectionCompositeAsset.class;
                } else {
                    return FolderCompositeAsset.class;
                }
            }

            String category = getCategory();
            if (category == null) {
                return InvalidAsset.class;
            }

            // call the sub type asset manager to get more details
            AssetManager assetManager = (AssetManager) DefaultAssetManager.getAssetSubTypeManager(category);
            if (assetManager!=null) {
                return assetManager.getAssetClass();
            } else {
                // Last is just an attachment item
                if (doc.getAttachmentList().size() > 0) {
                    return AttachmentAsset.class;
                }
                return Asset.class;
            }
        } else {
            return ProtectedAsset.class;
        }
    }

    public String getAssetType() {
        return getAssetClass().getSimpleName().replaceAll("Asset$", "");
    }

    public boolean isFolder() {
        if (doc.getObjectNumbers(Constants.COMPOSITE_ASSET_CLASS) == 0){
            return false;
        } else {
            // Work around a bug XWIKI-1624
            // TODO: Remove the work-around once XWIKI-1624 is fixed
            List<BaseObject> compObjs = doc.getObjects(Constants.COMPOSITE_ASSET_CLASS);
            for (BaseObject compObj : compObjs){
                if (compObj != null){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isCollection() {
        if (isFolder()) {
            com.xpn.xwiki.api.Object obj = getObject(Constants.COMPOSITE_ASSET_CLASS);
            if (getValue(Constants.COMPOSITE_ASSET_CLASS_TYPE, obj).equals(Constants.COMPOSITE_ASSET_CLASS_TYPE_COLLECTION)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRootCollection() {
        if (isFolder()) {
            com.xpn.xwiki.api.Object obj = getObject(Constants.COMPOSITE_ASSET_CLASS);
            if (getValue(Constants.COMPOSITE_ASSET_CLASS_TYPE, obj).equals(Constants.COMPOSITE_ASSET_CLASS_TYPE_ROOT_COLLECTION)) {
                return true;
            }
        }
        return false;
    }


    static public Asset fetchAsset(String assetName, XWikiContext context) throws XWikiException {
        com.xpn.xwiki.api.XWiki xwikiApi = new com.xpn.xwiki.api.XWiki(context.getWiki(), context);
        Document doc = xwikiApi.getDocument(assetName);

        if (doc instanceof Asset){
            return ((Asset) doc).as(null);
        } else {
            throw new AssetException(AssetException.ERROR_ASSET_NOT_FOUND, "Asset "+assetName+" could not be found");
        }
    }

    static public Asset fetchAsset(String web, String page, XWikiContext context) throws XWikiException {
        return Asset.fetchAsset(web+"."+page, context);
    }

    public List<Property> getMetadata() {
        List<Property> md = new ArrayList<Property>();

        com.xpn.xwiki.api.Object assetObj = getObject(Constants.ASSET_CLASS);
        for (java.lang.Object prop : assetObj.getPropertyNames()) {
            LOG.debug("Adding "+prop+" to metadata list");
            md.add(assetObj.getProperty((String) prop));
        }

        com.xpn.xwiki.api.Object licenseObj = getObject(Constants.ASSET_LICENCE_CLASS);
        for (java.lang.Object prop : licenseObj.getPropertyNames()) {
            LOG.debug("Adding "+prop+" to metadata list");
            md.add(licenseObj.getProperty((String) prop));
        }

        // Add title now that it isn't in the asset object
        BaseStringProperty baseProp = new BaseStringProperty();
        baseProp.setName("title");
        baseProp.setValue(getTitle());
        Property prop = new Property(baseProp, context);
        md.add(prop);

        // Add creator
        baseProp = new BaseStringProperty();
        baseProp.setName("creator");
        baseProp.setValue(getCreator());
        prop = new Property(baseProp, context);
        md.add(prop);

        // Add creator's name
        String creatorName =  context.getWiki().getLocalUserName(context.getUser(), null, false, context);
        baseProp = new BaseStringProperty();
        baseProp.setName("creatorName");
        baseProp.setValue(creatorName);
        prop = new Property(baseProp, context);
        md.add(prop);

        // Add page's name
        baseProp = new BaseStringProperty();
        baseProp.setName("assetpage");
        baseProp.setValue(getFullName());
        prop = new Property(baseProp, context);
        md.add(prop);

        // Add rights List
        Map<String,Boolean> rightsList = getRightsList();
        String rightsListString = "{";
        for (String right : rightsList.keySet()) {
            if (rightsListString.length()>1) {
                rightsListString += ",";
            }
            rightsListString += "\""+right+"\":"+(rightsList.get(right)?"true":"false");
        }
        rightsListString += "}";

        baseProp = new BaseStringProperty();
        baseProp.setName("rightsList");
        baseProp.setValue(rightsListString);
        prop = new Property(baseProp, context);
        md.add(prop);

        // Add subcategory
        baseProp = new BaseStringProperty();
        baseProp.setName("subcategory");
        baseProp.setValue(getCategorySubtype());
        prop = new Property(baseProp, context);
        md.add(prop);

        // Add assetType to metadata
        Class assetType = getAssetClass();
        baseProp = new BaseStringProperty();
        baseProp.setName("fullAssetType");
        baseProp.setValue(assetType.getCanonicalName());
        prop = new Property(baseProp, context);
        md.add(prop);

        // And add shortAssetType to metadata
        String shortAssetType = assetType.getSimpleName();
        if (!shortAssetType.equals("Asset")) {
            shortAssetType = getAssetType();
        }
        baseProp = new BaseStringProperty();
        baseProp.setName("assetType");
        baseProp.setValue(shortAssetType);
        prop = new Property(baseProp, context);
        md.add(prop);

        baseProp = new BaseStringProperty();
        baseProp.setName("revision");
        baseProp.setValue(getVersion());
        prop = new Property(baseProp, context);
        md.add(prop);

        return md;
    }

    public <A extends Asset> A as(Class<? extends Asset> wantedClass) throws XWikiException {
        return (A) as(wantedClass, false);
    }

    public <A extends Asset> A subclassAs(Class<? extends Asset> wantedClass) throws XWikiException {
        return (A) this.as(wantedClass, true);
    }

    public <A extends Asset> A as(Class<? extends Asset> wantedClass, boolean moreSpecific) throws XWikiException {
        // If this is already a subtype of the wanted class then just return this
        if (wantedClass != null && wantedClass.isAssignableFrom(this.getClass())) {
            return (A) this;
        }

        Class returnClass;

        // Make sure the determined type is a subclass of the wanted type
        Class<? extends Asset> assetType = getAssetClass();
        if (!moreSpecific) {
            if (wantedClass == null) {
                // Return as whatever subtype it is
                returnClass = assetType;
            } else if (wantedClass.isAssignableFrom(assetType)) {
                // Return as the desired type
                returnClass = wantedClass;
            } else {
                throw new AssetException(AssetException.ERROR_ASSET_INCOMPATIBLE, "Document of type "+assetType+" cannot become "+wantedClass);
            }
        } else {
            // Subclass an Asset
            if (assetType.equals(wantedClass)) {
                // Already is that type, so don't initialize
                moreSpecific = false;
                returnClass = wantedClass;
            } else if (assetType.equals(Asset.class) && assetType.isAssignableFrom(wantedClass)) {
                // Turn into a specified subtype of Asset (initializing the subtype)
                returnClass = wantedClass;
            } else {
                throw new AssetException(AssetException.ERROR_ASSET_INCOMPATIBLE, "Document of type "+assetType+" cannot become "+wantedClass);
            }
        }

        Class[] parameterTypes = new Class[2];
        parameterTypes[0] = XWikiDocument.class;
        parameterTypes[1] = XWikiContext.class;

        java.lang.Object[] initargs = new java.lang.Object[2];
        initargs[0] = doc;
        initargs[1] = context;

        A subtyped;
        try {
            Constructor<A> constructor = returnClass.getConstructor(parameterTypes);
            subtyped = constructor.newInstance(initargs);
            if (moreSpecific) {
                subtyped.initSubType();
            }
        } catch (Exception e) {
            throw new AssetException(AssetException.ERROR_ASSET_INCOMPATIBLE, "Document of type "+assetType+" cannot become "+wantedClass, e);
        }

        return subtyped;
    }

    public void inheritMetadata() throws XWikiException {
        inheritMetadata(null, null);
    }

    public void inheritMetadata(String parentAsset) throws XWikiException {
        inheritMetadata(parentAsset, null);
    }

    public void inheritMetadata(String parentAsset, String publishSpace) throws XWikiException {
        assertCanEdit();
        XWikiDocument assetDoc = getDoc();

        BaseObject assetObj = assetDoc.getObject(Constants.ASSET_CLASS);
        if (assetObj == null) {
            assetObj = assetDoc.getObject(Constants.ASSET_CLASS, true, context);
        }

	boolean licenceSet = false;
        BaseObject newLicenceObj = doc.getObject(Constants.ASSET_LICENCE_CLASS);
        if (newLicenceObj == null) {
            newLicenceObj = doc.newObject(Constants.ASSET_LICENCE_CLASS, context);
        }

        // CURRIKI-2451 - Make sure group rights are used by default
        if (publishSpace != null && publishSpace.startsWith(Constants.GROUP_COLLECTION_SPACE_PREFIX)) {
            String groupSpace = publishSpace.replaceFirst("^"+Constants.GROUP_COLLECTION_SPACE_PREFIX, Constants.GROUP_SPACE_PREFIX);
            String rights = Constants.ASSET_CLASS_RIGHT_PUBLIC;

            // TODO: This should probably be using the SpaceManager extension
            XWikiDocument groupSpaceDoc = context.getWiki().getDocument(groupSpace+"."+Constants.GROUP_RIGHTS_PAGE, context);
            if (groupSpaceDoc != null){
                // Note that the values for the group access defaults
                //  DO NOT MATCH the values that need to be applied to the collection
                BaseObject rObj = groupSpaceDoc.getObject(Constants.GROUP_RIGHTS_CLASS);
                if (rObj != null){
                    String groupDefaultPrivs = rObj.getStringValue(Constants.GROUP_RIGHTS_PROPERTY);
                    if (groupDefaultPrivs.equals(Constants.GROUP_RIGHT_PRIVATE)){
                        rights = Constants.ASSET_CLASS_RIGHT_PRIVATE;
                    } else if (groupDefaultPrivs.equals(Constants.GROUP_RIGHT_PROTECTED)){
                        rights = Constants.ASSET_CLASS_RIGHT_MEMBERS;
                    } else if (groupDefaultPrivs.equals(Constants.GROUP_RIGHT_PUBLIC)){
                        rights = Constants.ASSET_CLASS_RIGHT_PUBLIC;
                    }


                    // CURRIKI-4377 - inherit grade level, topic, language, and licence from group
                    if (parentAsset == null || parentAsset.length() == 0) {
                        // educationLevel
                        List edLevels = rObj.getListValue(Constants.GROUP_DEFAULT_GRADE_PROPERTY);
                        assetObj.setDBStringListValue(Constants.ASSET_CLASS_EDUCATIONAL_LEVEL, edLevels);

                        // topic
                        List topics = rObj.getListValue(Constants.GROUP_DEFAULT_TOPIC_PROPERTY);
                        assetObj.setDBStringListValue(Constants.ASSET_CLASS_FRAMEWORK_ITEMS, topics);

                        // language
                        String lang = rObj.getStringValue(Constants.GROUP_DEFAULT_LANGUAGE_PROPERTY);
                        assetObj.setStringValue(Constants.ASSET_CLASS_LANGUAGE, lang);

                        // licence
                        String licence = rObj.getStringValue(Constants.GROUP_DEFAULT_LICENCE_PROPERTY);
                        newLicenceObj.setStringValue(Constants.ASSET_LICENCE_ITEM_LICENCE_TYPE, licence);
			licenceSet = true;
                    }
                }
            }

            assetObj.setStringValue(Constants.ASSET_CLASS_RIGHT, rights);
        }

        XWikiDocument parentDoc = null;
        if (parentAsset != null && parentAsset.length() > 0) {
            parentDoc = context.getWiki().getDocument(parentAsset, context);
            if (parentDoc.isNew()) {
                throw new AssetException(AssetException.MODULE_PLUGIN_ASSET, AssetException.ERROR_ASSET_NOT_FOUND, "Parent asset not found");
            }
        }
        // the Root collection does not have a proper asset class so we can't inherit from it
        if (parentDoc != null && parentDoc.getObject(Constants.ASSET_CLASS) != null && !parentDoc.getName().equals(Constants.ROOT_COLLECTION_PAGE)) {
            BaseObject parentAssetObj = (BaseObject) parentDoc.getObject(Constants.ASSET_CLASS).clone();

            copyProperty(parentAssetObj, assetObj, Constants.ASSET_CLASS_EDUCATIONAL_LEVEL);
            copyProperty(parentAssetObj, assetObj, Constants.ASSET_CLASS_FRAMEWORK_ITEMS);
            copyProperty(parentAssetObj, assetObj, Constants.ASSET_CLASS_RIGHT);
            copyProperty(parentAssetObj, assetObj, Constants.ASSET_CLASS_KEYWORDS);
            copyProperty(parentAssetObj, assetObj, Constants.ASSET_CLASS_LANGUAGE);
        }

        // make sure default rights value is not empty (default to public)
        String rights = assetObj.getStringValue(Constants.ASSET_CLASS_RIGHT);
        if ((rights==null)||(rights.equals(""))) {
            assetObj.setStringValue(Constants.ASSET_CLASS_RIGHT, Constants.ASSET_CLASS_RIGHT_PUBLIC);
        }

        // the Root collection does not have an asset Licence class
        if (parentDoc != null && parentDoc.getObject(Constants.ASSET_LICENCE_CLASS) != null) {
            BaseObject parentLicenceObjAsset = parentDoc.getObject(Constants.ASSET_LICENCE_CLASS);
            copyProperty(parentLicenceObjAsset, newLicenceObj, Constants.ASSET_LICENCE_ITEM_LICENCE_TYPE);
        } else if (!licenceSet) {
            newLicenceObj.setStringValue(Constants.ASSET_LICENCE_ITEM_LICENCE_TYPE, Constants.ASSET_LICENCE_ITEM_LICENCE_TYPE_DEFAULT);
        }

        // Rights holder should be by default the pretty name of the user
        newLicenceObj.setStringValue(Constants.ASSET_LICENCE_ITEM_RIGHTS_HOLDER, context.getWiki().getLocalUserName(context.getUser(), null, false, context));
    }

    /**
     * Make a folder in the current asset
     * @return
     * @throws XWikiException
     */
    public FolderCompositeAsset makeFolder() throws XWikiException {
        return makeFolder(null);
    }

    /**
     * Make a folder in the current asset
     * @param page
     * @return
     * @throws XWikiException
     */
    public FolderCompositeAsset makeFolder(String page) throws XWikiException {
        assertCanEdit();
        FolderCompositeAsset asset = subclassAs(FolderCompositeAsset.class);

        if (page != null) {
            asset.addSubasset(page);
        }
        saveDocument(context.getMessageTool().get("curriki.comment.createfoldersourceasset"), true);
        return asset;
    }

    /**
     * Make a collection in the current asset
     * @return
     * @throws XWikiException
     */
    public CollectionCompositeAsset makeCollection() throws XWikiException {
        return makeCollection(null);
    }

    /**
     * Make a composite collection in the current asset
     * @param page
     * @return
     * @throws XWikiException
     */
    public CollectionCompositeAsset makeCollection(String page) throws XWikiException {
        assertCanEdit();
        CollectionCompositeAsset asset = subclassAs(CollectionCompositeAsset.class);

        if (page != null) {
            asset.addSubasset(page);
        }
        saveDocument(context.getMessageTool().get("curriki.comment.createfoldersourceasset"), true);
        return asset;
    }

    /**
     * Make an attachment asset based on the type of attachment
     * This will call the appropriate subtype asset manager for further processing
     * @return an AttachmentAsset object
     * @throws XWikiException
     */
    public AttachmentAsset processAttachment() throws XWikiException {
        assertCanEdit();
        AttachmentAsset asset = subclassAs(AttachmentAsset.class);

        if (doc.getAttachmentList().size() > 0) {
            XWikiAttachment attach = doc.getAttachmentList().get(0);
            determineFileTypeAndCategory(attach);
            saveDocument(context.getMessageTool().get("curriki.comment.createtextsourceasset"), true);
        }

        return asset;
    }

    /**
     * This functions determines the file type and the category based on an attachment
     * @param attachment
     * @throws XWikiException
     */
    protected void determineFileTypeAndCategory(XWikiAttachment attachment) throws XWikiException {
        String filename = attachment.getFilename();
        String extension = (filename.lastIndexOf(".") != -1 ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(): null);
        MimeTypePlugin mimePlugin = getMimeTypePlugin();
        String filetype =  mimePlugin.getFileType(extension, context);
        String category = mimePlugin.getCategory(filetype, context);
        XWikiDocument assetDoc = getDoc();

        // set the attachment information
        BaseObject documentObject = assetDoc.getObject(Constants.ATTACHMENT_ASSET_CLASS, true, context);
        documentObject.setStringValue(Constants.ATTACHMENT_ASSET_FILE_TYPE, filetype);
        documentObject.setLongValue(Constants.ATTACHMENT_ASSET_FILE_SIZE, attachment.getFilesize());

        // set the category
        BaseObject assetObj = assetDoc.getObject(Constants.ASSET_CLASS, true, context);
        assetObj.setStringValue(Constants.ASSET_CLASS_CATEGORY, category);

        // call the sub type asset manager to get more details
        AssetManager assetManager = DefaultAssetManager.getAssetSubTypeManager(category);
        if (assetManager!=null) {
            assetManager.updateSubAssetClass(assetDoc, filetype, category, attachment, context);
        }
    }

    protected MimeTypePlugin getMimeTypePlugin() {
        MimeTypePlugin mimePlugin = (MimeTypePlugin) context.getWiki().getPlugin(MimeTypePlugin.PLUGIN_NAME, context);
        return mimePlugin;
    }

    public Boolean isPublished() {
        return !getSpace().equals("AssetTemp");
    }

    public Asset publish(String space) throws XWikiException {
        return publish(space, true);
    }

    public Asset publish(String space, String name) throws XWikiException {
        return publish(space, name, true);
    }

    public Asset publish(String space, boolean checkSpace) throws XWikiException {
        String prettyName = context.getWiki().clearName(getTitle(), true, true, context);

        return publish(space, prettyName, checkSpace);
    }

    public Asset publish(String space, String name, boolean checkSpace) throws XWikiException {
        if (isPublished()) {
            throw new AssetException("This resource is already published");
        }

        assertCanEdit();

        XWikiDocument assetDoc = getDoc();
        // Be sure we have a category first -- attachments can't get them automatically yet
        BaseObject obj = assetDoc.getObject(Constants.ASSET_CLASS);
        if (obj != null) {
            String category = obj.getStringValue(Constants.ASSET_CLASS_CATEGORY);
            if (category == null || category.length() == 0 || category.equals(Constants.ASSET_CATEGORY_UNKNOWN)) {
                processAttachment();
            }
        }

        if (!space.startsWith(Constants.COLLECTION_PREFIX)) {
            throw new AssetException("You cannot publish to the space "+space);
        }

        if (!validate()) {
            throw new AssetException("Validation failed.");
        }

        if (checkSpace) {
            CollectionSpace.ensureExists(space, context);
        }

        // Let's choose a nice name for the page
        String prettyName = context.getWiki().clearName(name, true, true, context);
        //rename(space + "." + context.getWiki().getUniquePageName(space, prettyName.trim(), context), new ArrayList<String>());
        this.doc = assetDoc.copyDocument(space + "." + context.getWiki().getUniquePageName(space, prettyName.trim(), context),context);
        context.getWiki().deleteDocument(assetDoc, context);

        applyRightsPolicy();

        List<String> params = new ArrayList<String>();
        params.add(assetDoc.getStringValue(Constants.ASSET_CLASS_CATEGORY));
        save(context.getMessageTool().get("curriki.comment.finishcreatingsubasset", params));

        if (isCollection()) {
            RootCollectionCompositeAsset root = CollectionSpace.getRootCollection(space, context);
            root.addSubasset(this.getFullName());
            root.saveDocument(context.getMessageTool().get("curriki.comment.addtocollectionasset"));
        }

        return this;
    }

    public boolean validate() throws XWikiException {
        // Has the asset been subtyped ?
        if (getAssetClass().equals(Asset.class)) {
            throw new AssetException("This asset is not complete.");
        }

        // Other validation

        // Super's validate
        return super.validate();
    }

	/**
	 *
	 * @param appropriatePedagogy
	 * @param contentAccuracy
	 * @param technicalCompletness
	 * @return the calculated rating
	 * @throws XWikiException
	 */

    public String calculateRating(String appropriatePedagogy, String contentAccuracy,String technicalCompletness)throws XWikiException{

    	//validate that a value was selected in all categories
    	if(appropriatePedagogy==null || "".equals(appropriatePedagogy)  ||
    			contentAccuracy==null || "".equals(contentAccuracy) ||
    			technicalCompletness==null || "".equals(technicalCompletness)){
    		throw new AssetException(context.getMessageTool().get("curriki.crs.review.mustSelectAValueInAllCategories"));
    	}
    	//validate that only appropriatePedagogy is not rated
    	//or all criteria are not rated
    	if (!appropriatePedagogy.equals("0") && (contentAccuracy.equals("0") || technicalCompletness.equals("0"))) {
    		throw new AssetException(context.getMessageTool().get("curriki.crs.review.notValidNotRatedCategorySelection"));
    	}
    	if (appropriatePedagogy.equals("0") &&
    			((contentAccuracy.equals("0") && !technicalCompletness.equals("0"))||(!contentAccuracy.equals("0") && technicalCompletness.equals("0")))) {
    		throw new AssetException(context.getMessageTool().get("curriki.crs.review.notValidNotRatedCategorySelection"));
    	}

    	int valueAppropriatePedagogy=Integer.parseInt(appropriatePedagogy);
    	int valueContentAccuracy=Integer.parseInt(contentAccuracy);
    	int valueTechnicalCompletness=Integer.parseInt(technicalCompletness);

    	float numerator = valueAppropriatePedagogy+valueContentAccuracy+valueTechnicalCompletness;

    	int rating;
	if(valueAppropriatePedagogy!=0){
		rating=Math.round(numerator/3);
	} else {
		rating=Math.round(numerator/2);
	}

    	return String.valueOf(rating);
    }

    /**
     * get the numbers of comments, the reviews are counted as a comment
     * @return comment numbers
     */
    public Integer getCommentNumbers()
    {
    	return getComments().size() + getObjectNumbers(Constants.ASSET_CURRIKI_REVIEW_CLASS);
    }

    public List getCommentsByDate() {
        List comments = getComments();
        Collections.sort(comments, new CommentsSorter());
        return comments;
    }


    public boolean canBeNominatedOrReviewed(){
    	use(Constants.ASSET_CLASS);
        String rights = (String)getValue(Constants.ASSET_CLASS_RIGHT);

        //false if access privilege is PRIVATE
        if (rights!=null && rights.equals(Constants.ASSET_CLASS_RIGHT_PRIVATE)) {
         return false;
        }
        //false if CRS.CurrikiReviewStatusClass.status == P (Partner)
    	use(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS);
        String status = (String)getValue(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS_STATUS);

        if (status!=null && status.equals(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS_STATUS_PARTNER)) {
            return false;
        }

        //false if BFCS is SPECIALCHECKREQUIRED,IMPROVEMENTREQUIRED or DELETEDFROMMEMBERACCESS
        use(Constants.ASSET_CLASS);
        String fcStatus = (String)getValue(Constants.ASSET_BFCS_STATUS);
        if(fcStatus!=null && !fcStatus.equals("")){
        	if(fcStatus.equals(Constants.ASSET_BFCS_STATUS_SPECIALCHECKREQUIRED) ||
        		fcStatus.equals(Constants.ASSET_BFCS_STATUS_IMPROVEMENTREQUIRED) ||
        		fcStatus.equals(Constants.ASSET_BFCS_STATUS_DELETEDFROMMEMBERACCESS)){
        		return false;
        	}
        }

    	return true;
    }

    /**
     * If BFCS other than â€œOKâ€� was applied or the resource is set to Private,
     * the resource should be removed from the Review Queue and no longer show Nominate or Review links.
     */
    public void checkReviewQueue(){
    	use(Constants.ASSET_CLASS);
        String fcStatus = (String)getValue(Constants.ASSET_BFCS_STATUS);
        if(fcStatus!=null && !fcStatus.equals("")){
        	if(fcStatus.equals(Constants.ASSET_BFCS_STATUS_SPECIALCHECKREQUIRED) ||
        		fcStatus.equals(Constants.ASSET_BFCS_STATUS_IMPROVEMENTREQUIRED) ||
        		fcStatus.equals(Constants.ASSET_BFCS_STATUS_DELETEDFROMMEMBERACCESS)){
        		removeFromReviewQueue();
        		return;
        	}
        }

        String rights = (String)getValue(Constants.ASSET_CLASS_RIGHT);

        //false if access privilege is PRIVATE
        if (rights!=null && rights.equals(Constants.ASSET_CLASS_RIGHT_PRIVATE)) {
        	removeFromReviewQueue();
        }

    }

    /**
     * Remove the asset from the review queue
     */
    public void removeFromReviewQueue(){
        // TODO:  Something here caused CURRIKI-3100 making the rest of the metadata not get saved
        //        Could be the use of "use" as that is not being used elsewhere here ?
        //use(Constants.ASSET_CURRIKI_REVIEW_CLASS);

        XWikiDocument assetDoc = getDoc();
        BaseObject obj = assetDoc.getObject(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS);

        if (obj != null) {
            Integer reviewpending = obj.getIntValue("reviewpending");
            if(reviewpending!=null && reviewpending.equals(1)){
                obj.setIntValue("reviewpending", 0);
            }
        }

    }

    /**
     * Nominate a resource for review
     * @param comments
     * @return
     * @throws XWikiException
     */
    public Asset nominate(String comments) throws XWikiException {
        XWikiDocument assetDoc = getDoc();
        BaseObject obj = assetDoc.getObject(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS);

        String suser =  context.getUser();
        if (obj==null) {
            obj = assetDoc.newObject(Constants.ASSET_CURRIKI_REVIEW_STATUS_CLASS, context);
            obj.setIntValue("number",0);
        }
        obj.setStringValue("nomination_user", suser);
        obj.setDateValue("nomination_date", new Date());
        obj.setLargeStringValue("nomination_comment", comments);

        obj.setIntValue("reviewpending", 1);

        saveWithProgrammingRights("save CRS nomination");

    	return this;
    }


   /**
    * Data Model migration code
    * Determines if an asset is in the latest format 
    * @return boolean true if latest format
    */
   public boolean isLatestVersion() throws XWikiException {     
     if (getObject(Constants.OLD_ASSET_CLASS)!=null)
      return false;

     if (getObject(Constants.OLD_EXTERNAL_ASSET_CLASS)!=null)
      return false;

     return true;
   }

    /**
     * Data Model Migration Code
     * @param newAssetObject
     * @param oldAssetObject
     * @param propname
     */
    private void updateObject(Object newAssetObject, Object oldAssetObject, String propname) {
        updateObject(newAssetObject, oldAssetObject, propname, propname);
    }

    /**
     * Data Model Migration Code
     * @param newAssetObject
     * @param oldAssetObject
     * @param newpropname
     * @param oldpropname
     */
    private void updateObject(Object newAssetObject, Object oldAssetObject, String newpropname, String oldpropname) {
        if (LOG.isDebugEnabled())
         LOG.debug("CURRIKI CONVERTER: updating property " + newpropname);

        use(oldAssetObject);
        java.lang.Object value = getValue(oldpropname);
        use(newAssetObject);
        set(newpropname, value);

        if (LOG.isDebugEnabled())
         LOG.debug("CURRIKI CONVERTER: updated property value is " + newAssetObject.get(newpropname));

    }

    /**
     * Conversion of the category and sub type classes
     * @throws XWikiException
     */
    private void setNewCategoryAndClass() throws XWikiException {
        if (LOG.isDebugEnabled())
         LOG.debug("CURRIKI CONVERTER: running setNewCategoryAndClass");

        // Get Asset objects
        Object oldAssetObject = getObject(Constants.OLD_ASSET_CLASS);

        // get old category
        if (oldAssetObject!=null)
           use(oldAssetObject);

        String oldCategory = (String) getValue(Constants.ASSET_CLASS_CATEGORY);
        String newCategory = "";

        if (oldCategory==null)
           oldCategory = "";
        
        // transforming category of type text
        if (oldCategory.equals(Constants.OLD_CATEGORY_COLLECTION)||(getObject(Constants.OLD_COMPOSITE_ASSET_CLASS)!=null)) {

            newCategory = Constants.ASSET_CATEGORY_COLLECTION;
            // we don't need the composite asset object
            Object oldCompositeAssetObject = getObject(Constants.OLD_COMPOSITE_ASSET_CLASS);
            Object newCompositeAssetObject = getObject(Constants.COMPOSITE_ASSET_CLASS, true);

            if (oldCompositeAssetObject!=null) {
                removeObject(oldCompositeAssetObject);
                updateObject(newCompositeAssetObject, oldCompositeAssetObject, Constants.COMPOSITE_ASSET_CLASS_TYPE);
                use(newCompositeAssetObject);
                if (Constants.OLD_COMPOSITE_ASSET_CLASS_TYPE_SUBFOLDER.equals(getValue(Constants.COMPOSITE_ASSET_CLASS_TYPE)))
                    set(Constants.COMPOSITE_ASSET_CLASS_TYPE, Constants.COMPOSITE_ASSET_CLASS_TYPE_SUBFOLDER);
            } else {
                if (LOG.isErrorEnabled())
                    LOG.error("CURRIKI ASSET CONVERTER ERROR: asset declared collection has no composite class for asset " + getFullName());
            }

            // convert sub-assets
            List subassets = getObjects(Constants.OLD_SUBASSET_CLASS);
            if ((subassets!=null) && (subassets.size()>0)) {
                for (int i=0; i<subassets.size();i++) {
                    Object oldSubasset = (Object) subassets.get(i);
                    Object newSubasset = newObject(Constants.SUBASSET_CLASS);
                    updateObject(newSubasset, oldSubasset, Constants.SUBASSET_CLASS_ORDER);
                    updateObject(newSubasset, oldSubasset, Constants.SUBASSET_CLASS_PAGE);
                }
                removeObjects(Constants.OLD_SUBASSET_CLASS);
            }

            // convert reorder info
            List reorderedlist = getObjects(Constants.OLD_COLLECTION_REORDERED_CLASS);
            if ((reorderedlist!=null) && (reorderedlist.size()>0)) {
                for (int i=0; i<reorderedlist.size();i++) {
                    Object oldReorderdObject = (Object) reorderedlist.get(i);
                    Object newReorderdObject = newObject(Constants.COLLECTION_REORDERED_CLASS);
                    updateObject(newReorderdObject, oldReorderdObject, Constants.COLLECTION_REORDERED_CLASS_REORDERD);
                }
                removeObjects(Constants.OLD_COLLECTION_REORDERED_CLASS);
            }
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_LINK)||(getObject(Constants.OLD_EXTERNAL_ASSET_CLASS)!=null)) {
            newCategory = Constants.ASSET_CATEGORY_EXTERNAL;
            Object newExternalAssetObject = getObject(Constants.EXTERNAL_ASSET_CLASS, true);
            Object oldExternalAssetObject = getObject(Constants.OLD_EXTERNAL_ASSET_CLASS);
            if (oldExternalAssetObject!=null) {
                updateObject(newExternalAssetObject, oldExternalAssetObject, Constants.EXTERNAL_ASSET_LINK, Constants.OLD_EXTERNAL_ASSET_LINK);
                removeObject(oldExternalAssetObject);
            }

            // make sure we don't have this object anymore since a bad previous migration could have caused it
            Object attachmentAssetObject = getObject(Constants.ATTACHMENT_ASSET_CLASS);
            if (attachmentAssetObject!=null)
                removeObject(attachmentAssetObject);
            // make sure we don't have this object anymore since a bad previous migration could have caused it
            Object imageAssetObject = getObject(Constants.IMAGE_ASSET_CLASS);
            if (imageAssetObject!=null)
                removeObject(imageAssetObject);
            // make sure we don't have this object anymore since it could exist previously for external assets
            Object oldImageAssetObject = getObject(Constants.OLD_MIMETYPE_PICTURE_CLASS);
            if (oldImageAssetObject!=null)
                removeObject(oldImageAssetObject);
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_TEXT)||(getObject(Constants.OLD_TEXT_ASSET_CLASS)!=null)) {
            Object newTextAssetObject = getObject(Constants.TEXT_ASSET_CLASS, true);
            set(Constants.TEXT_ASSET_SYNTAX, Constants.TEXT_ASSET_SYNTAX_TEXT);
            Object oldTextAssetObject = getObject(Constants.OLD_TEXT_ASSET_CLASS);
            if (oldTextAssetObject!=null) {
                newCategory = Constants.ASSET_CATEGORY_TEXT;
                use(oldTextAssetObject);
                Long type = (Long) getValue(Constants.OLD_TEXT_ASSET_CLASS_TYPE);
                use(oldTextAssetObject);
                String content = (String) getValue(Constants.OLD_TEXT_ASSET_CLASS_TEXT);
                if (content==null)
                 setContent("");
                else
                 setContent(content);
                use(newTextAssetObject);

                if ((type==null)||type==0) {
                    set(Constants.TEXT_ASSET_SYNTAX, Constants.TEXT_ASSET_SYNTAX_XWIKI1);
                } else if (type==1) {
                    set(Constants.TEXT_ASSET_SYNTAX, Constants.TEXT_ASSET_SYNTAX_XHTML1);
                } else if (type==2) {
                    set(Constants.TEXT_ASSET_SYNTAX, Constants.TEXT_ASSET_SYNTAX_XWIKI1);
                } else {
                    set(Constants.TEXT_ASSET_SYNTAX, Constants.TEXT_ASSET_SYNTAX_TEXT);
                }
                removeObject(oldTextAssetObject);
            }
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_ARCHIVE)||(getObject(Constants.OLD_MIMETYPE_ARCHIVE_CLASS)!=null)) {
            newCategory = Constants.ASSET_CATEGORY_ARCHIVE;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);
            Object oldArchiveAssetObject = getObject(Constants.OLD_MIMETYPE_ARCHIVE_CLASS);
            Object newArchiveAssetObject = getObject(Constants.ARCHIVE_ASSET_CLASS, true);
            if (oldArchiveAssetObject!=null) {
                updateObject(newArchiveAssetObject, oldArchiveAssetObject, Constants.ARCHIVE_ASSET_START_FILE, Constants.OLD_MIMETYPE_ARCHIVE_CLASS_DEFAULT_FILE);
                // we need to remove the old archive object
                removeObject(oldArchiveAssetObject);
            }
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_ANIMATION)) {
            newCategory = Constants.ASSET_CATEGORY_INTERACTIVE;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_IMAGE)) {
            newCategory = Constants.ASSET_CATEGORY_IMAGE;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);
            Object oldImageAssetObject = getObject(Constants.OLD_MIMETYPE_PICTURE_CLASS);
            Object newImageAssetObject = getObject(Constants.IMAGE_ASSET_CLASS, true);
            if (oldImageAssetObject!=null) {
                updateObject(newImageAssetObject, oldImageAssetObject, Constants.IMAGE_ASSET_WIDTH);
                updateObject(newImageAssetObject, oldImageAssetObject, Constants.IMAGE_ASSET_HEIGHT);
            }
            // we need to remove the old image object

            removeObject(oldImageAssetObject);
        } else if (getObject(Constants.OLD_VIDITALK_CLASS)!=null) {
            newCategory = Constants.ASSET_CATEGORY_VIDEO;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);

            // transfer video id
            Object newVideoAssetObject = getObject(Constants.VIDEO_ASSET_CLASS, true);
            Object oldVideoAssetObject = getObject(Constants.OLD_VIDITALK_CLASS);
            if (oldVideoAssetObject!=null) {
                use(oldVideoAssetObject);
                String videoId = (String) getValue(Constants.OLD_VIDITALK_CLASS_VIDEO_ID);
                use(newVideoAssetObject);
                if (videoId!=null) {
                    set(Constants.VIDEO_ASSET_ID, videoId);
                    set(Constants.VIDEO_ASSET_PARTNER, Constants.VIDEO_ASSET_PARTNER_VIDITALK);
                }          
                removeObject(oldVideoAssetObject);
            }
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_AUDIO)) {
            // we cannot determine type between audio and video
            newCategory = null;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);
        } else if (oldCategory.equals(Constants.OLD_CATEGORY_DOCUMENT)) {
            newCategory = Constants.ASSET_CATEGORY_DOCUMENT;
            getObject(Constants.ATTACHMENT_ASSET_CLASS, true);
        }

        // update document (attachment) assets.
        // We need to do this either because the document use to be declared as a Document
        // We also need to do this if the document is not a text asset and there is an attachment
        Object newDocumentAssetObject = getObject(Constants.ATTACHMENT_ASSET_CLASS);
        if ((!Constants.ASSET_CATEGORY_TEXT.equals(newCategory)
                && (getAttachmentList().size() > 0)) || (newDocumentAssetObject!=null))
        {
            updateObject(newDocumentAssetObject, oldAssetObject, Constants.ATTACHMENT_ASSET_ALT_TEXT, Constants.OLD_ASSET_CLASS_ALT_TEXT);
            updateObject(newDocumentAssetObject, oldAssetObject, Constants.ATTACHMENT_ASSET_CAPTION_TEXT, Constants.OLD_ASSET_CLASS_CAPTION_TEXT);
            XWikiAttachment attachment = (doc.getAttachmentList().size()>0) ? doc.getAttachmentList().get(0) : null;
            if (attachment!=null)
             determineFileTypeAndCategory(attachment);
            else {
                if (LOG.isErrorEnabled())
                   LOG.error("CURRIKI ASSET CONVERTER ERROR: attachment missing in document asset " + getFullName());
            }

            // temporary log for debugging
            if (LOG.isInfoEnabled()) {
                LOG.info("CURRIKI ASSET CONVERTER: detected category is " + getCategory());
            }

            // we should have found the same category through conversion
            if (newCategory!=null) {
                if (!newCategory.equals(getCategory())) {
                    if (LOG.isErrorEnabled())
                        LOG.error("CURRIKI ASSET CONVERTER ERROR: newCategory " + getCategory() + " different than converted category " + newCategory + " for asset " + getFullName());
                }

                // let's fallback to the new Category
                if (getCategory()==Constants.ASSET_CATEGORY_UNKNOWN)
                     setCategory(newCategory);                
            }


            // make sure we don't have this object anymore
            Object oldArchiveAssetObject = getObject(Constants.OLD_MIMETYPE_ARCHIVE_CLASS);
            if (oldArchiveAssetObject!=null)
             removeObject(oldArchiveAssetObject);
        } else {
            // for text, collection, external, collection assets
            setCategory(newCategory);
        }
    }

    /**
     * Hacked function needed by the Curriki Plugin
     * This is needed for the data model migration in case of a rollback item
     */
    protected void setAlreadyCloned() {
        cloned = true;
    }

    /**
     * Data Model Migration Code
     * Convert an asset from the old format to the new format
     *
     */
    public boolean convert() throws Exception {
        if (!hasProgrammingRights()) {
            java.lang.Object[] args = { getFullName() };
            throw new XWikiException(XWikiException.MODULE_XWIKI_ACCESS, XWikiException.ERROR_XWIKI_ACCESS_DENIED,
                    "Access denied with no programming rights document {0}", null, args);
        }
        
        if (convertWithoutSave()) {
            try {
                // Saving as a minor edit
                // We bypass the public api so that the author is not updated
                // We reset setContentDirty so that the content date does not change
                getDoc().setContentDirty(false);
                context.getWiki().saveDocument(getDoc(), context.getMessageTool().get("curriki.comment.datamodelmigration"), true, context);
                return true;
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("CURRIKI ASSET CONVERTER ERROR: error saving converted asset " + getFullName(), e);
                throw e;
            }
        }

        return false;
    }

    /**
     * Data Model Migration Code
     * Convert an asset from the old format to the new format
     */
    public boolean convertWithoutSave() throws Exception {
        if (isLatestVersion()) {
            if (LOG.isDebugEnabled())
                    LOG.debug("CURRIKI CONVERTER: asset already converted " + getFullName());
            return false;
        }

        try {
            // Convert Asset Class
            // we need to make sure we are working on the modified object
            // because getObject(classname, boolean) does not do the job right
            getDoc();

            Object oldAssetObject = getObject(Constants.OLD_ASSET_CLASS);
            Object newAssetObject = getObject(Constants.ASSET_CLASS, true);

            if (LOG.isDebugEnabled())
             LOG.debug("CURRIKI CONVERTER: running convert on asset " + getFullName());

            if (oldAssetObject!=null) {
                // set title
                use(oldAssetObject);
                String title = (String) getValue(Constants.OLD_ASSET_CLASS_TITLE);
                if (title!=null)
                    setTitle(title);
                setContent("");

                // transfer unchanged fields
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_DESCRIPTION);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_FRAMEWORK_ITEMS);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_HIDDEN_FROM_SEARCH);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_KEYWORDS);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_LANGUAGE);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_RIGHT);

                // transfer tracking field
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_TRACKING);
                // transfer file check fields
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_FCSTATUS);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_FCREVIEWER);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_FCDATE);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_FCNOTES);

                // transfer changed fields
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_INSTRUCTIONAL_COMPONENT, Constants.OLD_ASSET_CLASS_INSTRUCTIONAL_COMPONENT);
                updateObject(newAssetObject, oldAssetObject, Constants.ASSET_CLASS_EDUCATIONAL_LEVEL, Constants.OLD_ASSET_CLASS_EDUCATIONAL_LEVEL);
            }
            
            // create new category field
            setNewCategoryAndClass();

            if (oldAssetObject!=null) {
                // remove old asset object
                removeObject(oldAssetObject);
            }

            // Convert License Class
            Object oldLicenseObject = getObject(Constants.OLD_ASSET_LICENCE_CLASS);
            Object newLicenseObject = getObject(Constants.ASSET_LICENCE_CLASS, true);
            if (oldLicenseObject!=null) {
                updateObject(newLicenseObject, oldLicenseObject, Constants.ASSET_LICENCE_ITEM_LICENCE_TYPE, Constants.OLD_ASSET_LICENCE_ITEM_LICENCE_TYPE);
                updateObject(newLicenseObject, oldLicenseObject, Constants.ASSET_LICENCE_ITEM_RIGHTS_HOLDER);
                updateObject(newLicenseObject, oldLicenseObject, Constants.ASSET_LICENCE_ITEM_EXTERNAL_RIGHTS_HOLDER);
                updateObject(newLicenseObject, oldLicenseObject, Constants.ASSET_LICENCE_ITEM_EXPIRY_DATE);

                // remove old asset license object
                removeObject(oldLicenseObject);
            }

            return true;
        } catch (Exception e) {
            if (LOG.isErrorEnabled())
                LOG.error("CURRIKI ASSET CONVERTER ERROR: error converting asset " + getFullName(), e);
            throw new XWikiException(XWikiException.MODULE_XWIKI_PLUGINS, XWikiException.ERROR_XWIKI_UNKNOWN, "Curriki asset conversion exception", e);
        }
    }

    /**
       * Returns the first attachment in the list
       * @return
       */
      protected Attachment getFirstAttachment() {
          List list = getAttachmentList();
          if (list.size()==0)
              return null;
          Attachment attach = (Attachment) list.get(0);
          return attach;
      }

    /**
     * This function allows to replace the attachment from a file upload
     * It also reruns the determine filetype and category function
     * @return
     */
    public boolean replaceAttachment() {
        if (!hasProgrammingRights())
            return false;

        if (LOG.isDebugEnabled())
            LOG.debug("Getting first attachment");

        Attachment attach = getFirstAttachment();
        XWikiAttachment attachment = (attach==null) ? null : attach.getAttachment();

        FileUploadPlugin fileupload = (FileUploadPlugin) context.getWiki().getPlugin("fileupload",context);
        String name = "file";
        boolean newFileName = false;
        byte[] data = new byte[0];
        try {
            data = fileupload.getFileItemData(name, context);
            if ((data!=null)&&(data.length>0)) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Found data for attachment");
                String fname = fileupload.getFileName(name, context);
                int i = fname.lastIndexOf("\\");
                if (i==-1)
                    i = fname.lastIndexOf("/");
                String filename = fname.substring(i+1);
                filename = filename.replaceAll("\\+"," ");

                if (attachment==null) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Creating attachment from scratch attachment");
                    newFileName = true;
                    attachment = new XWikiAttachment();
                    doc.getAttachmentList().add(attachment);
                    // Add the attachment to the document
                    attachment.setDoc(doc);
                } else if (attachment.getFilename()!=filename) {
                    // filename is different we need to delete the previous attachment
                    if (LOG.isDebugEnabled())
                        LOG.debug("Deleting previous attachment");
                    newFileName = true;
                    doc.deleteAttachment(attachment, context);
                    attachment.setFilename(filename);
                    doc.getAttachmentList().add(attachment);
                    // Add the attachment to the document
                    attachment.setDoc(doc);
                }

                // now save the attachment under the new name
                if (LOG.isDebugEnabled())
                    LOG.debug("Saving attachment");
                attachment.setContent(data);
                attachment.setFilename(filename);
                attachment.setAuthor(context.getUser());
                doc.setAuthor(context.getUser());
                doc.saveAttachmentContent(attachment, context);

                // if it's a new file name we should run the category updater
                if (newFileName)
                    determineFileTypeAndCategory(attachment);

                return true;
            } else {
                // no attachment
                if (LOG.isDebugEnabled())
                    LOG.debug("No attachment found");
                return false;
            }
        } catch (XWikiException e) {
            e.printStackTrace();
            context.put("exception", e);
            return false;
        }
    }


    public boolean unLock() {
        try {
            XWikiLock lock = doc.getLock(context);
            if (lock != null )
                doc.removeLock(context);

            return true;
        } catch (XWikiException e) {
            return false;
        }
    }


    public class CommentsSorter implements Comparator {


        public int compare(java.lang.Object o1, java.lang.Object o2) {
            com.xpn.xwiki.api.Object o1a = (com.xpn.xwiki.api.Object) o1;
            com.xpn.xwiki.api.Object o2a = (com.xpn.xwiki.api.Object) o2;
            if (o1==null)
             return 1;
            if (o2==null)
             return -1;

            Date d1 = (Date) o1a.getProperty("date").getValue();
            Date d2 = (Date) o2a.getProperty("date").getValue();
            if (d1 == null)
             return 1;
            if (d2 == null)
             return -1;
                     
            return d1.compareTo(d2);
        }
    }

}

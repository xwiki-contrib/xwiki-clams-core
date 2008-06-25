// vim: ts=4:sw=4
/*global Ext */
/*global Curriki */
/*global _ */

Ext.ns('Curriki.ui');
Curriki.ui.InfoImg = '/xwiki/skins/curriki8/icons/exclamation.png';

Ext.ns('Curriki.ui.dialog');
Curriki.ui.dialog.Base = Ext.extend(Ext.Window, {
	 title:_('Untitled')
	,border:false
	,modal:true
	,width:634
	,minWidth:400
	,minHeight:100
	,maxHeight:575
	,autoScroll:false
	,constrain:true
	,collapsible:false
	,closable:false
	,resizable:false
	,shadow:false
	,defaults:{border:false}
	,listeners:{
		afterlayout:function(wnd, layout){
			if (this.afterlayout_maxheight) {
				// Don't collapse again
			} else {
				if (wnd.getBox().height > wnd.maxHeight){
					wnd.setHeight(wnd.maxHeight);
					wnd.center();
					this.afterlayout_maxheight = true;
				} else {
					wnd.setHeight('auto');
				}
			}
		}
	}
	,initComponent:function(){
		Curriki.ui.dialog.Base.superclass.initComponent.call(this);
	}
});

Curriki.ui.dialog.Actions = Ext.extend(Curriki.ui.dialog.Base, {
	 width:634
	,initComponent:function(){
		Curriki.ui.dialog.Actions.superclass.initComponent.call(this);
	}
});
Ext.reg('dialogueactions', Curriki.ui.dialog.Actions);

Curriki.ui.dialog.Messages = Ext.extend(Curriki.ui.dialog.Base, {
	 width:400
	,initComponent:function(){
		Curriki.ui.dialog.Messages.superclass.initComponent.call(this);
	}
});
Ext.reg('dialoguemessages', Curriki.ui.dialog.Messages);

Curriki.ui.show = function(xtype, options){
	var o = {xtype:xtype};
	Ext.apply(o,options);
	var p = Ext.ComponentMgr.create(o);

	p.show();
	Ext.ComponentMgr.register(p);
}

Ext.ns('Curriki.ui.treeLoader');
Curriki.ui.treeLoader.Base = function(config){
	Curriki.ui.treeLoader.Base.superclass.constructor.call(this);
};

Ext.extend(Curriki.ui.treeLoader.Base, Ext.tree.TreeLoader, {
		dataUrl:'DYNAMICALLY DETERMINED'
		,createNode:function(attr){
console.log('createNode: ',attr);

			if ('string' === typeof attr.id) {
				var parent = Curriki.ui.treeLoader.Base.superclass.createNode.call(this, attr);
console.log('createNode: parent',parent);
				return parent;
			}

			var childInfo = {
				 id:attr.assetpage||attr.collectionPage
				,text:attr.displayTitle
				,qtip:attr.description
				,cls:'resource-'+attr.assetType
				,allowDrag:false
				,allowDrop:false
			}

			if (attr.rights && !attr.rights.view){
				childInfo.text = _('TODO: Translation string about not viewable here');
				childInfo.qtip = undefined;
				childInfo.disabled = true;
				childInfo.allowDrop = false;
				childInfo.leaf = true;
				childInfo.cls = childInfo.cls+' rights-unviewable';
			} else if (attr.assetType && attr.assetType.search(/Composite$/) === -1){
				childInfo.leaf = true;
			} else if (attr.assetType){
				childInfo.leaf = false;
				childInfo.allowDrop = (attr.rights && attr.rights.edit);
			}

			// ?? = attr.order;

			Ext.apply(childInfo, attr);

			if(this.baseAttrs){
				Ext.applyIf(childInfo, this.baseAttrs);
			}
			if(this.applyLoader !== false){
				childInfo.loader = this;
			}
			if(typeof attr.uiProvider == 'string'){
			   childInfo.uiProvider = this.uiProviders[attr.uiProvider] || eval(attr.uiProvider);
			}

console.log('createNode: End ',childInfo);
			return(childInfo.leaf
			       ? new Ext.tree.TreeNode(childInfo)
			       : new Ext.tree.AsyncTreeNode(childInfo));
		}

		,requestData:function(node, callback){
			if (node.attributes.currikiNodeType === 'group'){
				this.dataUrl = '/xwiki/curriki/groups/'+node.attributes.id+'/collections';
			} else {
				this.dataUrl = '/xwiki/curriki/assets/'+node.attributes.id+'/subassets';
			}

			// From parent
			if(this.fireEvent("beforeload", this, node, callback) !== false){
				this.transId = Ext.Ajax.request({
					 method: 'GET'
					,url: this.dataUrl
					,disableCaching:true
					,headers: {
						'Accept':'application/json'
					}
					,success: this.handleResponse
					,failure: this.handleFailure
					,scope: this
					,argument: {callback: callback, node: node}
					,params: ''
				});
			} else {
				// if the load is cancelled, make sure we notify
				// the node that we are done
				if(typeof callback == "function"){
					callback();
				}
			}
		}
});

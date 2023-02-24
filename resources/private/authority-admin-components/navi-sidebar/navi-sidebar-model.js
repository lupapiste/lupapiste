// Authority admin navigation sidebar. The same component instance is
// used both for the static sidebar and the menu. The menu button is
// in the authority admin index.html.
LUPAPISTE.NaviSidebarModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.naviSidebarService;

  self.menu = service.userMenu;

  self.showMenu = service.showMenu;
  self.iconsOnly = self.disposedComputed( {
    read: function() {
      return service.iconsOnly() && !service.showMenu();
    },
    write: service.iconsOnly});

  _.map( self.menu, function( item ) {
    return _.set( item, "icon", _.set( {}, item.icon, true));
  });

  self.isCurrentPage = function( page ) {
    return lupapisteApp.models.rootVMO.isCurrentPage( page );
  };

  self.openPage = service.openPage;

  self.buttonCss = function() { return {"sidebar-button--icon": self.iconsOnly()}; };

  self.buttonTitle = function( item ) { return self.iconsOnly() ? item.loc : ""; };
};

LUPAPISTE.NaviSidebarService = function() {
  "use strict";
  var self = this;

  self.iconsOnly = ko.observable();
  self.showMenu = ko.observable();

  self.containerCss = ko.pureComputed( function ()  {
    return {"container--icon": self.iconsOnly()};
  } );

  self.menuCss = ko.pureComputed( function ()  {
    var menu = self.showMenu();
    return {"lupicon-menu": !menu,
            "lupicon-remove": menu};
  } );

  self.menuLoc = ko.pureComputed( function() {
    return self.showMenu() ? "close" : "navi-sidebar.menu";
  });


  ko.computed( function() {
    if( self.showMenu() ) {
      self.iconsOnly( false );
    }
  });

  self.openPage = function( item ) {
    pageutil.openPage( item.page );
    self.showMenu( false );
  };
};

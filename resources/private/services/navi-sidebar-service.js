LUPAPISTE.NaviSidebarService = function() {
  "use strict";
  var self = this;

  self.iconsOnly = ko.observable();
  self.showMenu = ko.observable();

  self.containerCss = ko.pureComputed( function ()  {
    return {"container--icon": self.iconsOnly()};
  } );

  var animate = ko.observable( 0 );

  self.menuCss = ko.computed( function ()  {
    var menu = self.showMenu();
    return {"lupicon-menu": !menu,
            "lupicon-remove": menu,
           "spin-once": animate()};
  } );

  self.menuLoc = ko.pureComputed( function() {
    return self.showMenu() ? "close" : "navi-sidebar.menu";
  });


  ko.computed( function() {
    if( self.iconsOnly()) {
      self.showMenu( false );
    }
  });

  self.showMenu.subscribe( function() {
    animate( true );
    _.delay( _.wrap( false, animate), 600);
  });

  self.openPage = function( item ) {
    pageutil.openPage( item.page );
    self.showMenu( false );
  };
};

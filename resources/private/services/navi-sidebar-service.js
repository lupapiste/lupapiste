// Sidebar service is needed to sync the menu button, container CSS
// classes and the actual sidebar component.
LUPAPISTE.NaviSidebarService = function() {
  "use strict";
  var self = this;

  self.iconsOnly = ko.observable();
  self.showMenu  = ko.observable();

  var animate = ko.observable();

  var menus = {
    authorityAdmin: [{icon: "lupicon-user",
                      page: "users",
                      loc: "auth-admin.users",
                      testId: "users"},
                     {icon: "lupicon-documents",
                      page: "applications",
                      loc: "auth-admin.application-settings",
                      testId: "application-settings"},
                     {icon: "lupicon-hammer",
                      page: "operations",
                      loc: "auth-admin.selected-operations"},
                     {icon: "lupicon-paperclip",
                      page: "attachments",
                      loc: "auth-admin.operations-attachments"},
                     {icon: "lupicon-external-link",
                      page: "backends",
                      loc: "auth-admin.backends"},
                     {icon: "lupicon-add-area",
                      page: "areas",
                      loc: "auth-admin.areas"},
                     {icon: "lupicon-document-list",
                      page: "reports",
                      loc: "auth-admin.reports"},
                     {icon: "lupicon-file-check",
                      page: "assignments",
                      loc: "auth-admin.assignments"},
                     {icon: "lupicon-stamp",
                      page: "stamp-editor",
                      loc: "auth-admin.stamp-editor",
                      testId: "stamp-editor"}],
    admin: [{icon: "lupicon-download",
             page: "admin",
             loc: "admin.xml"},
            {icon: "lupicon-user",
             page: "users",
             loc: "auth-admin.users",
             testId: "users"},
            {icon: "lupicon-circle-section-sign",
             page: "organizations",
             loc: "admin.organizations"},
            {icon: "lupicon-building",
             page: "companies",
             loc: "admin.companies"},
            {icon: "lupicon-flag",
             page: "features",
             loc: "admin.features"},
            {icon: "lupicon-refresh",
             page: "actions",
             loc: "admin.actions"},
            {icon: "lupicon-lock-open",
             page: "single-sign-on-keys",
             loc: "admin.sso"},
            {icon: "lupicon-presentation",
             page: "screenmessages",
             loc: "admin.screenmessages.list-title"},
            {icon: "lupicon-comment",
             page: "notifications",
             loc: "admin.user-notices"},
            {icon: "lupicon-document-list",
             page: "reports",
             loc: "auth-admin.reports"},
            {icon: "lupicon-megaphone",
             page: "campaigns",
             loc: "admin.campaigns",
             testId: "campaigns"},
            {icon: "lupicon-eye",
             page: "logs",
             loc: "admin.log",
             testId: "frontend-logs"}]};

  self.userMenu = ko.computed( function ()  {
    return menus[lupapisteApp.models.currentUser.role()];
  } );

  self.containerCss = ko.pureComputed( function ()  {
    return {"container--icon": self.iconsOnly()};
  } );

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
    if( self.iconsOnly() && self.showMenu()) {
      self.showMenu( false );
    }
  });

  // By adding and removing animation (see menuCss), we make sure that
  // it runs every time the menu is toggled.
  self.showMenu.subscribe( function() {
    animate( true );
    _.delay( _.wrap( false, animate), 600);
  });

  function close() {
    if( self.showMenu() ) {
      self.showMenu( false );
    }
  }

  self.openPage = function( item ) {
    pageutil.openPage( item.page );
    close();
  };

  // Pressing Esc closes menu
  hub.subscribe( "dialog-close", close );
  // Pressing outside menu closes menu
  $(document).on( "click", close );
};

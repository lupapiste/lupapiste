LUPAPISTE.NaviSidebarModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.naviSidebarService;

  self.menu = [{icon: "lupicon-user",
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
               testId: "stamp-editor"}];

  self.showMenu = service.showMenu;
  self.iconsOnly = self.disposedComputed( {
    read: function() {
      return service.iconsOnly() && !service.showMenu();
    },
    write: service.iconsOnly});

  if( features.enabled("ajanvaraus")
   && lupapisteApp.models.globalAuthModel.ok("calendars-enabled") ) {
    self.menu.push( {icon: "lupicon-calendar",
                page: "organization-calendars",
                loc: "auth-admin.organization-calendars"});
  }

  _.map( self.menu, function( item ) {
    return _.set( item, "icon", _.set( {}, item.icon, true));
  });

  self.isCurrentPage = function( page ) {
    return lupapisteApp.models.rootVMO.isCurrentPage( page );
  };

  self.openPage = service.openPage;

  self.buttonCss = function() {
    return {"sidebar-button--icon": self.iconsOnly()};
  };

  self.buttonTitle = function( item ) {
    return self.iconsOnly() ? item.loc : "";
  };


};

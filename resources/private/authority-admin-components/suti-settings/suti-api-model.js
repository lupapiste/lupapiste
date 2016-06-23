LUPAPISTE.SutiApiModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.readOnly = self.disposedPureComputed( function() {
    return !lupapisteApp.models.globalAuthModel.ok( "update-user-organization");
  });

  var visibleKeys = ["url", "username"];

  self.serverParams = {
    channel: {},
    server: ko.observable({}),
    readOnly: self.readOnly,
    waiting: ko.observable(false),
    header: "auth-admin.suti-api-settings.header",
    urlLabel: "auth-admin.suti-api-settings.urlLabel",
    saveLabel: "save",
    // define mandatory keys, but the default ajax error handling is used
    error: null,
    errorMessageTerm: null
  };

 self.serverParams.channel.send = function(e) {
    ajax.command("update-suti-server-details", e)
      .processing(self.serverParams.waiting)
      .success(function(resp){
        self.serverParams.server(_.pick(e, visibleKeys));
        util.showSavedIndicator(resp);
      })
      .call();
  };

  // Service
  var service = lupapisteApp.services.sutiService;

  self.disposedComputed( function() {
    self.serverParams.server( _.defaults( self.serverParams.server(),
                                          service.sutiDetails().server) );
  });

  service.fetchAdminDetails();
};

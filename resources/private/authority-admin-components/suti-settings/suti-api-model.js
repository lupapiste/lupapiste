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

  // Service
  var service = lupapisteApp.services.sutiService;

  self.serverParams.channel.send = function(e) {
    service.configureServer( e, self.serverParams.waiting );
  };

  self.disposedComputed( function() {
    self.serverParams.server( _.merge( self.serverParams.server(),
                                       service.sutiDetails().server) );
  });

};

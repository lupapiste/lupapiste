LUPAPISTE.SutiApiModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.readOnly = self.disposedPureComputed( function() {
    return !lupapisteApp.models.globalAuthModel.ok( "update-user-organization");
  });

  function emptyServerConf() {
    return {url: "", username: "", password: ""};
  }
  var visibleKeys = ["url", "username"];

  self.serverParams = {
    channel: {},
    server: ko.observable(emptyServerConf()),
    readOnly: self.readOnly,
    waiting: ko.observable(false),
    header: "auth-admin.suti-api-settings.header",
    urlLabel: "auth-admin.suti-api-settings.urlLabel",
    saveLabel: "save",
    // define mandatory keys, but the default ajax error handling is used
    error: null,
    errorMessageTerm: null
  };

  self.disposedComputed(function(){
    var server = util.getIn(params, ["organization", "suti", "server"]);
debug(server, params.organization.suti);
    self.serverParams.server(_.assign(emptyServerConf(), _.pick(server, visibleKeys)));
  });

  self.serverParams.channel.send = function(e) {
    ajax.command("update-suti-server-details", e)
      .processing(self.serverParams.waiting)
      .success(function(resp){
        self.serverParams.server(_.assign(emptyServerConf(), _.pick(e, visibleKeys)));
        util.showSavedIndicator(resp);
      })
      .call();
  };

};

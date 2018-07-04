LUPAPISTE.StateChangeMsgSettingsModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.url = ko.observable("");
  self.headers = ko.observableArray([]);
  self.authTypes = ["basic", "other"];
  self.authType = ko.observable("");
  self.basicUser = ko.observable("");
  self.basicPassword = ko.observable("");
  self.passwordPlaceholder = ko.observable("");

  self.disposedComputed( function() {
    var conf = params || {};
    self.url(conf.url);
    self.headers(conf.headers);
    self.authType(conf.authType);
    self.basicUser(conf.username);
    self.passwordPlaceholder(!_.isEmpty(conf.password) ? "********" : null);
  });

  self.add = function() {
    self.headers.push({name: "", value: ""});
  };

  self.remove = function(data) {
    self.headers.remove(data);
  };

  self.basicAuth = function() {
    return self.authType() === "basic";
  };

  self.save = function () {
    ajax.command("set-organization-state-change-endpoint",
      { url: self.url(),
        headers: self.headers(),
        authType: self.authType(),
        basicCreds: {username: self.basicUser(),
                     password: self.basicPassword()}})
      .success(util.showSavedIndicator)
      .error(util.showSavedIndicator)
      .call();
  };
};

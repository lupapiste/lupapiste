LUPAPISTE.StateChangeMsgSettingsModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.url = ko.observable(params.url);
  self.headers = ko.observableArray(params.headers);
  self.authTypes = ["basic", "other"];
  self.authType = ko.observable("");
  self.basicUser = ko.observable("");
  self.basicPassword = ko.observable("");

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

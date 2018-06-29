LUPAPISTE.StateChangeMsgSettingsModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.url = ko.observable(params.url);
  self.headers = ko.observableArray(params.headers);

  self.add = function() {
    self.headers.push({name: "", value: ""});
  };

  self.remove = function(data) {
    self.headers.remove(data);
  };

  self.save = function () {
    ajax.command("set-organization-state-change-endpoint",
          { url: self.url(),
            headers: self.headers()})
      .success(util.showSavedIndicator)
      .error(util.showSavedIndicator)
      .call();
  };
};

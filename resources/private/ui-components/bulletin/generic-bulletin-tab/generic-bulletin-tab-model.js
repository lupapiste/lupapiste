LUPAPISTE.GenericBulletinTabModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var appId = lupapisteApp.models.application.id;

  self.bulletinOpDescription = ko.observable().extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});
  self.opDescriptionIndicator = ko.observable().extend({notify: "always"});
  self.initialized = ko.observable(false);

  self.disposedComputed(function() {
    if (appId()) {
      self.bulletinOpDescription(lupapisteApp.models.application.bulletinOpDescription());

    }
  });

  self.disposedComputed(function() {
    var bulletinOpDescription = self.bulletinOpDescription();
    if (self.initialized()) {
      ajax.command("update-app-bulletin-op-description", {id: self.appId(), description: bulletinOpDescription})
        .success(function() {
          self.opDescriptionIndicator({type: "saved"});
        })
        .call();
    } else {
      self.initialized(true);
    }
  });
};

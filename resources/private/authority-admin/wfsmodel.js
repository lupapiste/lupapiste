var LUPAPISTE = LUPAPISTE || {};
LUPAPISTE.WFSModel = function () {
  "use strict";

  var self = this;

  self.data = ko.observable();
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.editUrl = ko.observable();
  self.editUsername = ko.observable();
  self.editPassword = ko.observable();
  self.versions = ko.observable([]);
  self.editVersion = ko.observable();
  self.editContext = null;
  self.error = ko.observable(false);

  self.load = function() {
    ajax.query("krysp-config")
      .success(function(d) {
        var data = d.krysp || [];
        // change map into a list where map key is one of the element keys
        // for easier handling with knockout
        self.data(_.map(_.keys(data), function(k) {
          var conf = data[k];
          conf.permitType = k;
          return conf;
        }));
      })
      .call();
  };

  self.save = function() {
    ajax.command("set-krysp-endpoint", {
        url: self.editUrl(),
        username: self.editUsername(),
        password: self.editPassword(),
        version: self.editVersion() || "",
        permitType: self.editContext.permitType
      })
      .success(function() {
        self.load();
        self.error(false);
        LUPAPISTE.ModalDialog.close();
      })
      .processing(self.processing)
      .pending(self.pending)
      .error(function(e) {
        self.error(e.text);
      })
      .call();
    return false;
  };

  self.openDialog = function(model) {
    var url = model.url || "";
    var username = model.username || "";
    var password = model.password || "";
    var version = model.version || "";
    var versionsAvailable = LUPAPISTE.config.kryspVersions[model.permitType];

    if (!versionsAvailable) {
      error("No supported KRYSP versions for permit type", model.permitType);
    }

    self.processing(false);
    self.pending(false);

    self.versions(versionsAvailable);
    self.editUrl(url);
    self.editUsername(username);
    self.editPassword(password);
    self.editVersion(version);
    self.editContext = model;
    self.error(false);
    LUPAPISTE.ModalDialog.open("#dialog-edit-wfs");
  };
};

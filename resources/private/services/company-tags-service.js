LUPAPISTE.CompanyTagsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observableArray();

  self.currentCompanyTags = ko.pureComputed(function() {
    if (self.data()) {
      return self.data();
    }
    return [];
  });

  function load(){
    if (lupapisteApp.models.globalAuthModel.ok("company-tags")) {
      ajax.query("company-tags")
        .success(function(res) {
          self.data(res.tags);
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }

  self.refresh = function() {
    load();
  };

};

LUPAPISTE.OrganizationTagsService = function() {
  "use strict";
  var self = this;

  var _data = ko.observable();

  self.data = ko.pureComputed(function() {
    return _data();
  });

  self.currentApplicationOrganizationTags = ko.pureComputed(function() {
    var currentOrganization = util.getIn(lupapisteApp, ["models", "application", "organization"]);
    if (currentOrganization && self.data()) {
      return util.getIn(self.data(), [currentOrganization, "tags"]);
    }
    return [];
  });

  function load(){
    if (lupapisteApp.models.globalAuthModel.ok("get-organization-tags")) {
      ajax.query("get-organization-tags")
        .success(function(res) {
          _data(res.tags);
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }
};

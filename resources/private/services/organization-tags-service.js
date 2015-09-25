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

  ajax.query("get-organization-tags")
  .success(function(res) {
    _data(res.tags);
  })
  .call();
};

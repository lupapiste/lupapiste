LUPAPISTE.OrganizationTagsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observable();

  self.currentApplicationOrganizationTags = ko.pureComputed(function() {
    var currentOrganization = ko.unwrap(lupapisteApp.models.application.organization);
    if (currentOrganization && self.data()) {
      return util.getIn(self.data(), [currentOrganization, "tags"]);
    }
    return [];
  });

  ajax.query("get-organization-tags")
  .success(function(res) {
    self.data(res.tags);
  })
  .error(_.noop)
  .call();
};

;(function() {
  "use strict";

  function OrganizationsModel() {
    var self = this;

    self.organizations = ko.observableArray([]);
    self.pending = ko.observable();

    self.load = function() {
      ajax
        .query("organizations")
        .pending(self.pending)
        .success(function(d) { self.organizations(d.organizations); })
        .call();
    };
  }

  var organizationsModel = new OrganizationsModel();

  hub.onPageChange("organizations", organizationsModel.load);

  $(function() {
    $("#organizations").applyBindings(organizationsModel);
  });

})();

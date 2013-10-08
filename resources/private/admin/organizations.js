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

  function EditOrganizationModel() {
    var self = this;

    self.organization = ko.observable();

    self.open = function(organization) {
      self.organization(organization);
      LUPAPISTE.ModalDialog.open("#dialog-edit-organization");
      return self;
    };

    self.execute = function(attachments) {
      alert("exe");
    };

    $(function() {
      self.selectm = $("#dialog-edit-organization .organizations").selectm();
      self.selectm
        .allowDuplicates(false)
        .ok(self.execute)
        .cancel(LUPAPISTE.ModalDialog.close);
    });
  }

  var editOrganizationModel = new EditOrganizationModel();

  hub.onPageChange("organizations", organizationsModel.load);

  $(function() {
    $("#organizations").applyBindings({ "organizationsModel": organizationsModel});
  });

})();

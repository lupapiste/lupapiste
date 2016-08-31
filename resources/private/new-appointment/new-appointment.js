;(function() {
  "use strict";

  var _model = new NewAppointmentPageModel();

  function NewAppointmentPageModel() {
    var self = this;
    self.applications = ko.observableArray([]);
    self.selectedApplicationId = ko.observable();
    self.selectedApplicationModel = { id: ko.observable() };
    self.selectedReservationType = ko.observable();

    self.selectedParty = ko.observable();
    self.availableAuthorities = ko.observable();
    self.reservationTypes = ko.observable();
    self.defaultLocation = ko.observable();

    self.initApplications = function(data) {
      self.applications(data);
    }
  }

  $(function() {
    $("#new-appointment").applyBindings({ model: _model });
  });

  if (features.enabled("ajanvaraus")) {
    hub.onPageLoad("new-appointment", function() {
      ajax.query("applications-for-new-appointment-page")
        .success(function (result) {
          _model.initApplications(result.data);
        })
        .call();
    });
  }

})();

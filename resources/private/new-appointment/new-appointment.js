;(function() {
  "use strict";

  var _model = new NewAppointmentPageModel();

  function NewAppointmentPageModel() {
    var self = this;
    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.applications = ko.observableArray([]);
    self.selectedApplication = ko.observable();

    self.bookAppointmentParams = { // for compatibility reasons client is an observable
                                   client: ko.observable(),
                                   application: ko.observable(),
                                   authorities: ko.observableArray([]),
                                   selectedParty: ko.observable(),
                                   reservationTypes: ko.observableArray([]),
                                   selectedReservationType: ko.observable(),
                                   defaultLocation: ko.observable() };

    self.disposedComputed(function() {
      var app = self.selectedApplication();
      if (!_.isEmpty(app)) {
        self.sendEvent("calendarService", "fetchApplicationCalendarConfig", {applicationId: app.id});
        self.bookAppointmentParams.application({id: ko.observable(app.id),
                                                organizationName: ko.observable(app.organizationName)});
      }
    });

    self.disposedComputed(function() {
      if (lupapisteApp.models.currentUser.loaded()) {
        self.bookAppointmentParams.client({id: lupapisteApp.models.currentUser.id(),
                                           firstName: lupapisteApp.models.currentUser.firstName(),
                                           lastName: lupapisteApp.models.currentUser.lastName()});
      }
    });

    self.addEventListener("calendarService", "applicationCalendarConfigFetched", function(event) {
      self.bookAppointmentParams.authorities(event.authorities);
      self.bookAppointmentParams.reservationTypes(event.reservationTypes);
      self.bookAppointmentParams.defaultLocation(event.defaultLocation);
    });

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

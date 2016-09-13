LUPAPISTE.ApplicantCalendarModel = function () {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.BaseCalendarModel());
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.bookAppointmentParams = { // for compatibility reasons client is an observable
                                 client: ko.observable({ firstName: lupapisteApp.models.currentUser.firstName(),
                                                         lastName: lupapisteApp.models.currentUser.lastName(),
                                                         id: lupapisteApp.models.currentUser.id(),
                                                         partyType: [] }),
                                 application: ko.observable(),
                                 authorities: ko.observableArray([]),
                                 selectedParty: ko.observable(),
                                 reservationTypes: ko.observableArray([]),
                                 selectedReservationType: ko.observable(),
                                 defaultLocation: ko.observable() };

  self.pendingNotifications = lupapisteApp.models.application.calendarNotificationsPending;

  self.disposedComputed(function() {
    var id = lupapisteApp.models.application.id();
    if (!_.isEmpty(id)) {
      self.sendEvent("calendarService", "fetchApplicationCalendarConfig", {applicationId: id});
      self.bookAppointmentParams.application({id: id, organizationName: lupapisteApp.models.application.organizationName()});
    }
  });

  self.addEventListener("calendarService", "applicationCalendarConfigFetched", function(event) {
    self.bookAppointmentParams.authorities(event.authorities);
    self.bookAppointmentParams.reservationTypes(event.reservationTypes);
    self.bookAppointmentParams.defaultLocation(event.defaultLocation);
  });

  self.acceptReservation = function(r) {
    self.sendEvent("calendarView", "updateOperationCalled");
    ajax
      .command("accept-reservation", {id: lupapisteApp.models.application.id(), reservationId: r.id()})
      .success(function() {
        r.acknowledged("accepted");
        self.sendEvent("calendarView", "updateOperationProcessed");
      })
      .call();
  };

  self.declineReservation = function(r) {
    self.sendEvent("calendarView", "updateOperationCalled");
    ajax
      .command("decline-reservation", {id: lupapisteApp.models.application.id(), reservationId: r.id()})
      .success(function() {
        r.acknowledged("declined");
        self.sendEvent("calendarView", "updateOperationProcessed");
      })
      .call();
  };

  self.appointmentParticipants = function(r) {
    return _.map(r.participants(), function (p) { return util.partyFullName(p); }).join(", ");
  };

};

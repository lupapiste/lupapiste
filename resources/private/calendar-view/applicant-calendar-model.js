// Input params:
// * calendarConfig: JSON object consisting of application specific calendar configuration:
//     - authorities: observable array of selectable authorities
//     - reservationTypes: observable array of selectable reservation types
//     - defaultLocation: observable containing the organization's default location for appointments  
LUPAPISTE.ApplicantCalendarModel = function (params) {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.BaseCalendarModel());

  self.bookAppointmentParams = { // for compatibility reasons client is an observable
                                 client: ko.observable({ firstName: lupapisteApp.models.currentUser.firstName(),
                                                         lastName: lupapisteApp.models.currentUser.lastName(),
                                                         id: lupapisteApp.models.currentUser.id(),
                                                         partyType: [] }),
                                 application: ko.observable(),
                                 authorities: params.calendarConfig.authorities,
                                 selectedParty: ko.observable(),
                                 reservationTypes: params.calendarConfig.reservationTypes,
                                 selectedReservationType: ko.observable(),
                                 defaultLocation: params.calendarConfig.defaultLocation };

  self.pendingNotifications = lupapisteApp.models.application.calendarNotificationsPending;

  self.disposedComputed(function() {
    var id = lupapisteApp.models.application.id();
    if (!_.isEmpty(id)) {
      self.bookAppointmentParams.application({id: id, organizationName: lupapisteApp.models.application.organizationName()});
    }
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

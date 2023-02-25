LUPAPISTE.CalendarNotificationListModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.items = params.items;
  self.actionRequired = params.actionRequired;
  self.linkToApplication = params.linkToApplication;

  self.acceptReservation = function(r) {
    self.sendEvent("calendarView", "updateOperationCalled");
    ajax
      .command("accept-reservation", {id: r.applicationId, reservationId: r.id})
      .success(function() {
        r.acknowledged("accepted");
        self.sendEvent("calendarView", "updateOperationProcessed");
      })
      .call();
  };

  self.declineReservation = function(r) {
    self.sendEvent("calendarView", "updateOperationCalled");
    ajax
      .command("decline-reservation", {id: r.applicationId, reservationId: r.id})
      .success(function() {
        r.acknowledged("declined");
        self.sendEvent("calendarView", "updateOperationProcessed");
      })
      .call();
  };

  self.markSeen = function(r) {
    ajax
      .command("mark-reservation-update-seen", {id: r.applicationId, reservationId: r.id})
      .success(function() {
        r.acknowledged("seen");
      })
      .call();
  };

  self.cancelReservation = function(reservation) {
    hub.send("show-dialog", {ltitle: "areyousure",
      size: "medium",
      component: "yes-no-dialog",
      componentParams: {ltext: "reservation.confirm-cancel",
                        yesFn: function() {
                          ajax
                            .command("cancel-reservation", { id: lupapisteApp.models.application.id(), reservationId: reservation.id() })
                            .success(function(response) {
                              util.showSavedIndicator(response);
                              reservation.acknowledged("canceled");
                            })
                            .error(util.showSavedIndicator).call();
                          return false;
                        }}});
  };

  self.appointmentParticipants = function(r) {
    return _.map(r.participants, function (p) { return util.partyFullName(p); }).join(", ");
  };

};
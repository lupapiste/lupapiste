LUPAPISTE.ApplicantCalendarModel = function () {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorities = ko.observableArray([]);
  self.reservationTypes = ko.observableArray([]);
  self.defaultLocation = ko.observable();

  self.client = ko.observable({ firstName: lupapisteApp.models.currentUser.firstName(),
                                lastName: lupapisteApp.models.currentUser.lastName(),
                                id: lupapisteApp.models.currentUser.id(),
                                partyType: [] });
  self.selectedParty = ko.observable();
  self.selectedReservationType = ko.observable();

  self.disposedComputed(function() {
    var id = lupapisteApp.models.application.id();
    if (!_.isEmpty(id)) {
      self.sendEvent("calendarService", "fetchApplicationCalendarConfig", {applicationId: id});
    }
  });

  self.addEventListener("calendarService", "applicationCalendarConfigFetched", function(event) {
    self.authorities(event.authorities);
    self.reservationTypes(event.reservationTypes);
    self.defaultLocation(event.defaultLocation);
  });

  self.acceptOrDeclineReservation = function(acceptOrDecline, r) {
    ajax
      .command(acceptOrDecline + "-reservation", {id: lupapisteApp.models.application.id(), reservationId: r.id})
      .success(function() {
        hub.send("indicator", {style: "positive"});
        repository.load(ko.unwrap(lupapisteApp.models.application.id));
      })
      .call();
  };

  self.appointmentParticipants = function(r) {
    return _.map(r.participants, function (p) { return util.partyFullName(p); }).join(", ");
  };

};

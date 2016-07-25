LUPAPISTE.ApplicantCalendarModel = function () {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorities = ko.observableArray([]);
  self.reservationTypes = ko.observableArray([]);

  self.selectedParty = ko.observable();
  self.selectedReservationType = ko.observable();

  self.disposedComputed(function() {
    var id = lupapisteApp.models.application.id();
    if (!_.isEmpty(id)) {
      self.sendEvent("calendarService", "fetchOrganizationReservationTypes", {applicationId: id});
      self.sendEvent("calendarService", "fetchAuthoritiesForApplication", {applicationId: id});
    }
  });

  self.addEventListener("calendarService", "organizationReservationTypesFetched", function(event) {
    self.reservationTypes(event.reservationTypes);
  });

  self.addEventListener("calendarService", "applicationAuthoritiesFetched", function(event) {
    self.authorities(event.authorities);
  });

  self.partyFullName = function(party) {
    return party.firstName + " " + party.lastName;
  };

};

LUPAPISTE.ApplicationAuthorityCalendarModel = function (params) {

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorizedParties = lupapisteApp.models.application.roles;
  self.reservationTypes = ko.observableArray([]);

  self.selectedParty = ko.observable();
  self.selectedReservationType = ko.observable();

  self.disposedComputed(function() {
    var organizationId = lupapisteApp.models.application.organization();
    if (!_.isEmpty(organizationId)) {
      self.sendEvent("calendarService", "fetchOrganizationReservationTypes", {organizationId: organizationId});
    }
  });

  self.addEventListener("calendarService", "organizationReservationTypesFetched", function(event) {
    self.reservationTypes(event.reservationTypes);
  });

  self.partyFullName = function(party) {
    return party.firstName() + " " + party.lastName();
  };

};

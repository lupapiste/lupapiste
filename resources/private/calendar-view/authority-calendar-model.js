LUPAPISTE.ApplicationAuthorityCalendarModel = function (params) {

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorizedParties = lupapisteApp.models.application.roles;

  self.selectedParty = ko.observable();
  self.selectedReservationTypes = ko.observableArray([]);

  self.disposedComputed(function() {
    var organizationId = lupapisteApp.models.application.organization();
    if (!_.isEmpty(organizationId)) {
      self.sendEvent("calendarService", "fetchOrganizationReservationTypes", {organizationId: organizationId});
    }
  });

  self.addEventListener("calendarService", "organizationReservationTypesFetched", function(event) {
    self.selectedReservationTypes(event.reservationTypes);
  });

  self.partyFullName = function(party) {
    return party.firstName() + " " + party.lastName();
  };

};

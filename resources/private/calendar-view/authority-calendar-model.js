LUPAPISTE.ApplicationAuthorityCalendarModel = function (params) {

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorizedParties = lupapisteApp.models.application.roles;
  self.reservationTypes = ko.observableArray([]);

  self.selectedParty = ko.observable();
  self.selectedReservationType = ko.observable();

  self.noCalendarFoundForOrganization = ko.observable();

  self.disposedComputed(function() {
    var organizationId = lupapisteApp.models.application.organization();
    if (!_.isEmpty(organizationId)) {
      self.sendEvent("calendarService", "fetchOrganizationReservationTypes", {organizationId: organizationId});
      self.sendEvent("calendarService", "fetchMyCalendars");
    }
  });

  self.addEventListener("calendarService", "organizationReservationTypesFetched", function(event) {
    self.reservationTypes(event.reservationTypes);
  });

  self.addEventListener("calendarService", "myCalendarsFetched", function(event) {
    if (!_.isEmpty(lupapisteApp.models.application.organization()) &&
        !_.find(event.calendars, { organization: lupapisteApp.models.application.organization() })) {
      self.noCalendarFoundForOrganization(true);
    }
  });

  self.partyFullName = function(party) {
    return party.firstName() + " " + party.lastName();
  };

};

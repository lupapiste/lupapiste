LUPAPISTE.ApplicationAuthorityCalendarModel = function () {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorizedParties = ko.observableArray([]);
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

  self.disposedComputed(function() {
    var parties = lupapisteApp.models.application.roles();
    var partyDocs = _.filter(lupapisteApp.models.application._js.documents, util.isPartyDoc);

    self.authorizedParties(
      _.map(parties, function (p) {
        var party = ko.mapping.toJS(p);
        var docs = _.concat(
            _.filter(partyDocs, { data: { userId: { value: party.id }}}),
            _.filter(partyDocs, { data: { henkilo: { userId: { value: party.id }}}}));
        var partyType = _.map(docs, function (d) { return _.get(d, "schema-info.name"); });
        return _.extend(party, { partyType: partyType });
      }));

    console.log(self.authorizedParties());
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
    return party.firstName + " " + party.lastName;
  };

};

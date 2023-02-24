// A component for DRYing up the organization reports view.
// Can provide different types of forms depending on omitted parameters:
// - A full form with all parameters that allows the user to select a date range for the report
// - A form without a title and/or caption, for displaying multiple forms under the same title
// - A form without a time range at all (by omitting all timestamp and date parameters)
// - A form with an unmodifiable time range (by omitting date observable parameters)
//
// Note that startTs, startDate, endTs and endDate are assumed to be observables/computed functions.
// Also note that startTs and endTs are assumed to be calculated from start/endDate if they exist.
// How you calculate the timestamp from the date is up to you, but convention is to have them form an inclusive range.
//
// Params [optional]:

// action                 Raw action name. The corresponding form action is /api/rawaction.
//                        If the action is not allowed no form is shown.
// [ltitle]               The form's title localization key
// [lcaption]             The form's caption (small text beneath title) localization key
// lbutton                The submit button's text's localization key
// [lstartDate]           The localization key for the "From date" - label
// [lendDate]             The localization key for the "Till date" - label
// [startTs]              The timestamp for the default minimum value for the time range
// [endTs]                The timestamp for the default maximum value for the time range
// [startDate]            The observable that the timestamp range minimum is calculated from, displayed as input
// [endDate]              The observable that the timestamp range maximum is calculated from, displayed as input
// [useOrgId]             If this is false, the organization id is not provided as a hidden input (defaults to true)

LUPAPISTE.OrganizationReportFormModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.action = params.action;

  self.showForm = self.disposedPureComputed( _.wrap( self.action,
                                                     lupapisteApp.models.globalAuthModel.ok ));


  self.ltitle = params.ltitle;
  self.lcaption = params.lcaption;
  self.lbutton = params.lbutton;
  self.lstartDate = params.lstartDate;
  self.lendDate = params.lendDate;

  self.startTs = params.startTs;
  self.endTs = params.endTs;

  self.startDate = params.startDate;
  self.endDate = params.endDate;

  self.useOrgId = !_.isNil(params.useOrgId) ? params.useOrgId : true;
  self.orgId = self.useOrgId && lupapisteApp.usagePurpose().orgId;

  // Ranges are grouped for convenience; if a new report needs just one endpoint this can be refactored without mercy
  self.hasTimestampRange = !_.isNil(params.startTs) && !_.isNil(params.endTs);
  self.hasDateRange = ko.isObservable(params.startDate) && ko.isObservable(params.endDate);


  self.badDates = self.disposedPureComputed( function() {
    if( self.hasDateRange ) {
      var start = self.startDate();
      var end = self.endDate();
      return !(start && end && start.getTime() <= end.getTime());
    }
  });
};

LUPAPISTE.NoticeFormsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  var service = lupapisteApp.services.noticeFormsService;
  var appAuthModel = lupapisteApp.models.applicationAuthModel;

  var config = {construction: {icon: "hammer"},
                terrain: {icon: "zoom-in-area"},
                location: {icon: "location-plus"}};

  self.waiting = ko.observable();
  self.view = ko.observable();
  self.noticeForms = self.disposedComputed( function() {
    return _( service.noticeForms() )
      .sortBy( [function( form ) {
        return _.indexOf( ["open", "ok", "rejected"], form.status.state );
      },
                "status.timestamp"])
      .groupBy( "type")
      .value();
  });

  self.formTypes = _.keys( config );
  self.formIcon = function( type ) {
    return _.get( config, type + ".icon");
  };

  self.canCreate = function( type ) {
    return appAuthModel.ok( sprintf( "new-%s-notice-form", type));
  };

  self.isVisible = self.disposedComputed( function() {
    return _.size(self.noticeForms())
      || _.some( self.formTypes, self.canCreate );
  });

  self.newFormAuth = function( type ) {
    return appAuthModel.ok( sprintf( "new-%s-notice-form", type));
  };

  self.formType = ko.observable();
  self.formReset = function() {
    self.view( "" );
    self.formType( "");
  };

  self.formOk = function() {
    self.formReset();

    service.fetchForms( true );
  };

  self.newForm = function( type ) {
    self.formType( type );
    self.view( "form");
  };

  self.formAssignments = function( type ) {
    return _.map( service.typeAssignments( type ),
                 function( assi ) {
                   var text = "";
                   var targets = _.size( assi.targets );
                   var firstName = _.trim( _.get( assi, "recipient.firstName"));
                   var lastName = _.trim( _.get( assi, "recipient.lastName" ));
                   if( firstName || lastName ) {
                     text += _.trim( firstName + " " + lastName ) + ": ";
                   }
                   text += loc(sprintf ("notice-form.assignment-%s.%s",
                                          targets > 1 ? "many" : "one",
                                          type),
                                 targets);
                   return {text: text, description: assi.description };
                 });

  };
};

// Configuration UI for a notice form type.
// Parameters [optional]:
//  organization: OrganizationModel instance.
//  authorities: Handler candidates (passed from NoticeFormConfigurationGroup)
//  formType: Notice form type (construction, terrain or location).
//  [showIntegration]: Include integration checkbox (default false).
LUPAPISTE.NoticeFormConfigurationModel = function( params ) {
  "use strict";
  var self = this;

  var enabled = ko.observable( true );

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel({enable: enabled}));

  self.disposedComputed( function() {
    enabled( Boolean(lupapisteApp.models.globalAuthModel.ok("toggle-organization-notice-form")) );
  });

  var organization = params.organization;
  self.formType = params.formType;
  self.languages = loc.getSupportedLanguages();
  self.assignmentsEnabled = organization.assignmentsEnabled;
  self.showIntegration = params.showIntegration;

  self.infoHeader = function( lang ) {
    return loc( "notice-forms.config.info", loc( "lang." + lang) );
  };

  var form =  util.getIn( organization, ["noticeForms", self.formType] );

  function command( cmd, params, cb ) {
    ajax.command( cmd, _.merge( params,
                                {organizationId: organization.organizationId(),
                                 type: self.formType }))
      .success( function( res ) {
        util.showSavedIndicator( res );
        if( cb ) {
          cb();
        }
      })
      .error( util.showSavedIndicator)
      .call();
  }

  self.formText = _.reduce( self.languages,
                            function( acc, lang ) {
                              var obs = ko.observable( util.getIn( form, ["text", lang], ""));
                              self.disposedSubscribe( obs, function( txt ) {
                                command( "set-organization-notice-form-text",
                                         {lang: lang, text: txt});
                              });
                              return _.set( acc, lang, obs);
                            },
                            {});

  self.formEnabled = ko.observable( util.getIn( form, ["enabled"]));
  self.disposedSubscribe( self.formEnabled,
                          function( flag ) {
                            command( "toggle-organization-notice-form",
                                     {enabled: flag},
                                     function() {
                                       // This is for automatic assignment filters list.
                                       var forms = organization.noticeForms();
                                       _.set( forms, self.formType + ".enabled", flag);
                                       hub.send( "notice-forms-changed");
                                     });
                          });

  self.formIntegration = ko.observable( util.getIn( form, ["integration"]));
  self.disposedSubscribe( self.formIntegration,
                          function( flag ) {
                            command( "toggle-organization-notice-form-integration",
                                     {enabled: flag});
                          });


  self.testId = function(  part ) {
    return sprintf( "notice-form-%s-%s", self.formType, part );
  };

};

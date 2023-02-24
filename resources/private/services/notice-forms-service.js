// Service managing notice forms and the related assignments.
LUPAPISTE.NoticeFormsService = function() {
  "use strict";
  var self = this;

  self.serviceName = "noticeFormsService";
  self.noticeForms = ko.observable();
  var authModels = ko.observable({});
  // Form open observables.
  var openObservables = {};
  var context = lupapisteApp.services.contextService;
  var assignment = lupapisteApp.services.assignmentService;

  function applicationId() {
    return context.applicationId();
  }

  self.formOpen = function( formId ) {
    return _.get( openObservables, formId );
  };

  self.refreshAuths = function() {
    ajax.query( "allowed-actions-for-category",
                {id: applicationId(),
                 category: "notice-forms"})
      .success( function( res ) {
        authModels( res.actionsById );
      })
      .error( _.wrap( {}, authModels ))
      .call();
  };

  self.hasAuth = function( formId, action ) {
    return util.getIn( authModels, [formId, action, "ok"]);
  };

  function hasAppAuth( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  self.fetchForms = function( refreshAttachments ) {
    ajax.query( "notice-forms", {id: applicationId(),
                                 lang: loc.getCurrentLanguage()})
      .success( function( res ) {
        openObservables = _.reduce( res.noticeForms,
                                    function( acc, form ) {
                                      return _.set( acc,
                                                    form.id,
                                                    self.formOpen( form.id )
                                                    || ko.observable( false ));
                                    },
                                    {});
        self.noticeForms( res.noticeForms );
        self.refreshAuths();
        hub.send( "assignmentService::applicationAssignments",
                  {applicationId: applicationId()});
      })
      .error( _.wrap( [], self.noticeForms))
      .call();
    if( refreshAttachments ) {
      lupapisteApp.services.attachmentsService.queryAll();
    }
  };

  function updateForm( action, formIdOrParams ) {
    var formId = _.get( formIdOrParams, "formId", formIdOrParams);
    if( self.hasAuth( formId, action )) {
      ajax.command( action,
                    _.merge( {id: applicationId()},
                             _.isObject( formIdOrParams )
                             ? formIdOrParams
                             : {formId: formId}))
        .success(function() {
          self.fetchForms( true );
        })
        .call();
    }
  }

  self.deleteForm = _.partial( updateForm, "delete-notice-form");
  self.approveForm = _.partial( updateForm, "approve-notice-form");
  self.rejectForm = _.partial( updateForm, "reject-notice-form");

  self.typeAssignments = function( type ) {
    var group =  "notice-forms-" + type;
    return _.filter( assignment.noticeFormAssignments(),
                   function( assi ) {
                     return group === _.get( assi, "targets.0.group");
                   });
  };

  function initialize() {
    if (hasAppAuth("notice-forms")) {
      self.fetchForms();
    }
  }

  hub.subscribe("application-model-updated", initialize);
  hub.subscribe("contextService::leave", function() {
    self.noticeForms( undefined );
  });
};

LUPAPISTE.ReviewTasksModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  function reviewProperty( task, propName ) {
    return _.get( task, "data.katselmus." + propName + ".value");
  }

  self.reviews = self.disposedComputed( function() {
    var app = lupapisteApp.models.application;
    return _( ko.mapping.toJS( app.tasks || [])
              .filter( function( task ) {
                return task["schema-info"].name === "task-katselmus";
              })
              .map(function( task ) {
                return {
                  notesVisible: ko.observable( false ),
                  hasAttachments: Boolean( _.find( app.attachments(),
                                                   function( aObs ) {
                                                     return _.get( ko.mapping.toJS( aObs ),
                                                                   "target.id") === task.id;
                                                   })),
                  name: task.taskname,
                  date: reviewProperty( task, "pitoPvm"),
                  author: reviewProperty( task, "pitaja"),
                  state: reviewProperty( task, "tila"),
                  condition: task.data.vaadittuLupaehtona.value,
                  notes: reviewProperty( task, "huomautukset.kuvaus")
                };
              })).value();
  });
};

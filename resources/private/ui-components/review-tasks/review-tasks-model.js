// Model for showing the review tasks table.
// Parameters:
// openTask: function to be called when the task name is
//           clicked. Receives task id as an argument.
LUPAPISTE.ReviewTasksModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.openTask = function( data ) {
    if( _.isFunction( params.openTask )) {
      params.openTask( data.id );
    }
  };

  function reviewProperty( task, propName ) {
    return _.get( task, "data.katselmus." + propName + ".value");
  }

  self.reviews = self.disposedComputed( function() {
    var app = lupapisteApp.models.application;
    var unsorted = _( ko.mapping.toJS( app.tasks || [])
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
                  condition: util.getIn(task, ["data", "vaadittuLupaehtona", "value"]),
                  notes: reviewProperty( task, "huomautukset.kuvaus"),
                  id: task.id,
                  source: task.source
                };
              })).value();
    // Create next links according to source.
    _.each( unsorted, function( review ) {
      if( review.source.type === "task") {
        var parent = _.find( unsorted, {id: review.source.id});
        parent.next = review;
      }
    });
    // Return the sorted reviews, where each review follows its source
    // review (if it has one).
    return _( unsorted )
      .filter( function( review ) {
        return review.source.type !== "task";
      })
      .reduce( function( acc, review ) {
        var xs = [review];
        while( review.next ) {
          xs.push( review.next );
          review = review.next;
        }
        return _.concat( acc, xs );
      }, []);
  });
};

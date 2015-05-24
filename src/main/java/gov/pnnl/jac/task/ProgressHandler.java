package gov.pnnl.jac.task;

/**
 * <p>Class for posting progress and message event for <code>Task</code>s</p>
 *
 * @author Grant Nakamura, R. Scarberry
 * @see Task
 * @version 1.0
 */
public class ProgressHandler {

    // Default starting and ending progress.
    public static final double DEFAULT_START_VALUE = 0.0;
    public static final double DEFAULT_END_VALUE   = 1.0;

    public static final double DEFAULT_MIN_PROGRESS_INC = 0.01;
    public static final long DEFAULT_MIN_TIME_INC = 500L; // Half a second.

    AbstractTask<?> mTask; // The task for the progress reporting.
    // It provides the mechanism for sending
    // the progress events.  If null, no events
    // are sent.

    Step mRootStep;     // Root of the progress tree
    Step mCurrentStep;  // Current step in progress tree

    // Current posted value -- initialized to a large negative number, so the
    // first call to post progress will work regardless of mMinValueInc.
    double mLastProgress = -Double.MAX_VALUE;
    // Time (ms) of the last post.
    long mLastTime;

    // Value that the current posting must exceed the previous posting in order
    // for the progress to actually be posted, unless mForcePost is set.
    double mMinProgressInc = DEFAULT_MIN_PROGRESS_INC;
    long mMinTimeInc = DEFAULT_MIN_TIME_INC;

    /**
     * Constructor which sets up a progress handler for step-by-step
     * progress posting.
     * <p>
     * @param t - the Task
     * @param begin - the beginning of the progress range.
     * @param end - the end of the progress range.
     * @param steps - the number of steps to get through the progress range.
     */
    public ProgressHandler (AbstractTask<?> t,
                            double begin,
                            double end,
                            int steps) {
        mTask = t;
        mCurrentStep = mRootStep = new Step (null, begin, end, steps);
    }

    /**
     * Constructor which sets up a progress handler for step-by-step
     * progress posting using progress endpoints obtained by calling the
     * <tt>getBeginProgress()</tt> and <tt>getEndProgress()</tt> methods of
     * the specified task object.
     * <p>
     * @param t - the Task
     * @param steps - the number of steps to get through the progress range.
     */
      public ProgressHandler (AbstractTask<?> t, int steps) {
        this(t, t.getBeginProgress(), t.getEndProgress(), steps);
      }

      /**
       * Constructor which sets up a progress handler for fractional
       * progress posting.  Calls to postStep() should do nothing.
       * <p>
       * @param t - the Task
       * @param begin - the beginning of the progress range.
       * @param end - the end of the progress range.
       */
      public ProgressHandler (AbstractTask<?> task,
                              double begin, double end) {
        this(task, begin, end, 0);
      }

      /**
       * Constructor which sets up a progress handler for fractional
       * progress posting with progress endpoints obtained by calling the
       * <tt>getBeginProgress()</tt> and <tt>getEndProgress()</tt> methods of
       * the specified task object.
       * Calls to postStep() should do nothing.
       * <p>
       * @param t - the Task
       */
      public ProgressHandler (AbstractTask<?> t) {
        this (t, t.getBeginProgress(), t.getEndProgress(), 0);
      }

      /**
       * Set the minimum progress change required for posting.  If the
       * change between the current post and the last is equal to or greater than
       * this value, the post will be made.  If less, the post is not made
       * unless the post is forced or if the time difference between posts
       * exceeds the min time increment.
       */
      public void setMinProgressIncrement (double minInc) {
          mMinProgressInc = minInc;
      }

      /**
       * Set a time increment for posting.  If the time between posts is
       * greater than or equal to this value, the post will be made regardless
       * of the progress change.
       * @param ms long
       */
      public void setMinTimeIncrement (long ms) {
          mMinTimeInc = ms;
      }

      /**
       * Post a message to <code>TaskListener</code>s registered with the
       * <code>Task</code>.
       */
      public void postMessage(String msg) {
        if (mTask != null) {           
          mTask.postMessage(msg);
        }
      }

      /**
       * Post a progress value with the option of forcing the post.
       */
      private void postValue (double value, boolean force) {
        if(force || okToPost(value)) {
          mLastProgress = value;
          mLastTime = System.currentTimeMillis();
          if (mTask != null) {
              mTask.postProgress(value);
          }
        }
      }

      /**
       * Post a progress value.
       */
      private void postValue (double value) {
        postValue(value, false);
      }

      /**
       * Is it ok to post the given progress value?  The answer is true if
       * either 1) the new progress is sufficiently greater than the last
       * progress posted, or 2) if sufficient time has passed since the last
       * progress post.
       * @param progress double
       * @return boolean
       */
      private boolean okToPost(double progress) {
          return (progress < 0.0 && mLastProgress >= 0.0) || (progress - mLastProgress) >= mMinProgressInc ||
                 (System.currentTimeMillis() - mLastTime) >= mMinTimeInc;
      }

      /**
       * Post the progress fraction.  The actual progress posted is the product of
       * this fraction and the progress range of the
       * current sequence or subsection.  The caller should ensure that the
       * argument increases between repeated calls to this method.
       */
      public void postFraction (double fraction) {
        mCurrentStep.postFraction(fraction);
      }

      /**
       * Post the next progress step.  This may not actually post to
       * the Task's listeners if the resulting progress doesn't sufficiently
       * exceed the last posted progress.
       */
      public void postStep() {
        mCurrentStep.postStep();
      }

      /**
       * Increment the current progress by the given number of steps and
       * post it.  This may not actually post to
       * the Task's listeners if the resulting progress doesn't sufficiently
       * exceed the last posted progress.
       */
      public void postSteps(int steps) {
        mCurrentStep.postSteps (steps);
      }

      /**
       * Post completion of the current progress section.
       */
      public void postEnd() {
        mCurrentStep.postEnd();
      }
      
      public void postIndeterminate() {
          postValue(-1.0);
      }

      /**
       * <p>Generate a new progress subsection from the given fraction of the
       * current section's progress range.</p>
       * <p>Example:</p>
       * <code>
       * ProgressHandler ph = new ProgressHandler (theTask);
       * ph.postBegin();
       * // Will divide into 3 subsections using fractions 0.5, 0.3, and 0.2
       * // (they should add up to 1.0)
       *
       * // The first subsection.
       * ph.subsection(0.5);
       * ph.postFraction(0.3);
       * ph.postFraction(0.7);
       * ph.postEnd();
       * // The second
       * ph.subsection(0.3);
       * ph.postFraction(0.5);
       * ph.postEnd();
       * // The third
       * ph.subsection(0.2);
       * ph.postEnd();
       *
       * // The root "end"
       * ph.postEnd();
       * </code>
       * @param fraction - the fraction of the current section's range that
       *   the subsection will consume.
       */
      public void subsection (double fraction) {
        subsection(fraction, 0);
      }

      /**
       * <p>Generate a new progress subsection set up for step-by-step posting
       * from the given fraction of the current section's progress range.</p>
       * <p>Example:</p>
       * <code>
       * ProgressHandler ph = new ProgressHandler (theTask);
       * ph.postBegin();
       * // Will divide into 3 subsections using fractions 0.5, 0.3, and 0.2
       * // (they should add up to 1.0)  Each subsection uses 10 steps.
       *
       * // The first subsection.
       * ph.subsection(0.5, 10);
       * for (int i=0; i<10; i++) {
       *   ph.postStep();
       * }
       * ph.postEnd();
       * // The second
       * ph.subsection(0.3, 10);
       * for (int i=0; i<10; i++) {
       *   ph.postStep();
       * }
       * ph.postEnd();
       * // The third
       * ph.subsection(0.2, 10);
       * for (int i=0; i<10; i++) {
       *   ph.postStep();
       * }
       * ph.postEnd();
       *
       * // The root "end"
       * ph.postEnd();
       * </code>
       * @param fraction - the fraction of the current section's range that
       *   the subsection will consume.
       * @param steps - the number of steps for the new subsection.
       */
      public void subsection (double fraction, int steps) {
        Step newStep = new Step(mCurrentStep, fraction, steps);
        mCurrentStep.mCurrent = newStep.mEndStep;
        mCurrentStep = newStep;
      }

      /**
       * <p>Generate a new progress subsection set up for step-by-step posting
       * from the next step of the current range.</p>
       */
      public void subsection (int steps) {
        double endValue = Math.min(mCurrentStep.mEndStep,
                                   mCurrentStep.mCurrent + mCurrentStep.mStepInc);
        Step newStep = new Step(mCurrentStep, mCurrentStep.mCurrent, endValue, steps);
        mCurrentStep.mCurrent = endValue;
        mCurrentStep = newStep;
      }

      /**
       * Take the current interval and turn it into a subsection configured for
       * fractional reporting.
       */
      public void subsection () {
        subsection(0);
      }

      /**
       * Post start of main interval
       */
      public void postBegin() {
        if(mCurrentStep == mRootStep && mCurrentStep.mCurrent == mCurrentStep.mBeginStep) {
          postFraction(0.0);
        }
      }

      // Post completion of main interval
      private void postRootEnd() {
        postValue(mRootStep.mEndStep, true);
      }

      /**
       * Get the current progress value.
       */
      public double getCurrentProgress() {
        return mCurrentStep.mCurrent;
      }

      /**
       * Helper class used to represent an interval.
       * This has no public constructors.
       */
      class Step {

          private Step mParent;
          private double mBeginStep;
          private double mEndStep;
          private double mCurrent;
          private double mStepInc;

          // Create a sequence
          Step (Step parent, double begin, double end, int steps) {
              mParent = parent;
              mBeginStep = mCurrent = begin;
              mEndStep = end;
              if (steps > 0) {
                  mStepInc = (end - begin)/steps;
              }
          }

          // Create a subsequence, with reported size relative to its parent's.
          Step (Step parent, double parentFraction, int steps) {
              mParent = parent;
              mBeginStep = mCurrent = mParent.mCurrent;
              mEndStep = mBeginStep + parentFraction*mParent.stepWidth();
              if (mEndStep > mParent.mEndStep) {
                  mEndStep = mParent.mEndStep;
              }
              if (steps > 0) {
                  mStepInc = (mEndStep - mBeginStep)/steps;
              }
          }

          double stepWidth() {
              return mEndStep - mBeginStep;
          }

          void postStep() {
              postSteps(1);
          }

          void postSteps(int steps) {
              if(steps >= 0) {
                  mCurrent = Math.min(mEndStep, mCurrent + steps*mStepInc);
                  postValue(mCurrent);
              }
          }

          void postFraction(double fraction) {
              if(fraction >= 0.0 && fraction <= 1.0) {
                  mCurrent = mBeginStep + fraction * stepWidth();
                  postValue(mCurrent);
              }
          }

          void postEnd() {
              postFraction(1.0);
              if(mParent != null) {
                  mCurrentStep = mParent;
              } else {
                  postRootEnd();
              }
          }
      }
}


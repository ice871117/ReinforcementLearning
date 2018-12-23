import tensorflow as tf
import numpy as np
import matplotlib.pyplot as plt


class DQN:
    Q_PREDICT = "q_predict"
    Q_TARGET = "q_target"

    def __init__(self, n_actions, n_features, learning_rate, epsilon, gamma, batch_size=50, memory_size=200,
                 out_graph=False):
        self.n_actions = n_actions
        self.n_features = n_features
        self.learning_rate = learning_rate
        self.gamma = gamma
        self.epsilon = epsilon
        self.batch_size = batch_size
        self.memory_size = memory_size
        self.tf_obs = tf.placeholder(tf.float32, [None, self.n_features], name="observations")
        self.tf_error = tf.placeholder(tf.float32, [None, self.n_actions], name="error")
        self.q_predict = self.build_net(self.Q_PREDICT)  # Q估计
        self.q_target = self.build_net(self.Q_TARGET)  # Q现实
        self.all_act_prob = tf.nn.softmax(self.q_predict, name='act_prob')
        self.loss = tf.losses.mean_squared_error(self.q_predict, self.tf_error)
        self.train_op = tf.train.RMSPropOptimizer(self.learning_rate).minimize(self.loss)
        self.memory_store = np.zeros((self.memory_size, n_features * 2 + 2))
        self.index = 0
        self.memory_count = 0
        self.counter = 0
        param_predict = tf.get_collection(self._get_param_name_by_scope(self.Q_PREDICT))
        param_target = tf.get_collection(self._get_param_name_by_scope(self.Q_TARGET))
        self.assign_op = [tf.assign(target, predict) for target, predict in zip(param_target, param_predict)]
        self.cost_his = []
        self.reward_sum = []
        self.sess = tf.Session()
        if out_graph:
            tf.summary.FileWriter("logs/", self.sess.graph)
        self.sess.run(tf.global_variables_initializer())

    def build_net(self, name):
        with tf.variable_scope(name):
            # c_names(collections_names) are the collections to store variables
            c_names, n_l1, w_initializer, b_initializer = \
                [self._get_param_name_by_scope(name), tf.GraphKeys.GLOBAL_VARIABLES], 10, \
                tf.random_normal_initializer(0., 0.3), tf.constant_initializer(0.1)  # config of layers

            # first layer. collections is used later when assign to target net
            with tf.variable_scope('l1'):
                w1 = tf.get_variable('w1', [self.n_features, n_l1], initializer=w_initializer, collections=c_names)
                b1 = tf.get_variable('b1', [1, n_l1], initializer=b_initializer, collections=c_names)
                l1 = tf.nn.relu(tf.matmul(self.tf_obs, w1) + b1)

            # second layer. collections is used later when assign to target net
            with tf.variable_scope('l2'):
                w2 = tf.get_variable('w2', [n_l1, self.n_actions], initializer=w_initializer, collections=c_names)
                b2 = tf.get_variable('b2', [1, self.n_actions], initializer=b_initializer, collections=c_names)
                return tf.matmul(l1, w2) + b2

    def choose_action(self, observation):
        observation = observation[np.newaxis, :]
        if np.random.uniform() < self.epsilon:
            # pure DQN has little difference using softmax or just logits to choose next action

            # single_logits = self.sess.run(self.q_predict, feed_dict={self.tf_obs: observation})
            # return np.argmax(single_logits)
            single_weights = self.sess.run(self.all_act_prob, feed_dict={self.tf_obs: observation})
            return np.random.choice(range(single_weights.shape[1]), p=single_weights.ravel())
        else:
            return np.random.choice(self.n_actions)

    def store_transition(self, s, a, r, s_):
        transition = np.hstack((s, [a, r], s_))
        self.memory_store[self.index % self.memory_size, :] = transition
        self.index += 1
        if self.memory_count < self.memory_size:
            self.memory_count += 1
        self.reward_sum.append(r)

    def do_learning(self):
        batch_transition, batch_size = self._peek_transition()

        q_predict_value = self.sess.run(self.q_predict, feed_dict={
            self.tf_obs: batch_transition[:, :self.n_features]})
        q_target_value = self.sess.run(self.q_target, feed_dict={
            self.tf_obs: batch_transition[:, -self.n_features:]})

        batch_action = batch_transition[:, self.n_features].astype(int)
        batch_reward = batch_transition[:, self.n_features + 1]
        error = q_predict_value.copy()
        error[[x for x in range(batch_size)], batch_action] = batch_reward + self.gamma * np.max(q_target_value, axis=1)

        loss, _ = self.sess.run([self.loss, self.train_op],
                                feed_dict={self.tf_error: error, self.tf_obs: batch_transition[:, :self.n_features]})

        self.cost_his.append(loss)
        self._assign_params_if_need()

    def plot_loss(self):
        plt.plot(np.arange(len(self.cost_his)), self.cost_his)
        plt.ylabel('Cost')
        plt.xlabel('training steps')
        plt.show()

    def _assign_params_if_need(self):
        self.counter += 1
        if self.counter % (self.memory_size // 2) == 0:
            print("assigning ")
            self.sess.run(self.assign_op)

    def _peek_transition(self):
        rows = np.random.choice(self.memory_count, size=self.batch_size)
        ret = self.memory_store[rows, :]
        return ret, len(ret)

    @staticmethod
    def _get_param_name_by_scope(scope_name):
        return scope_name + "_param"
